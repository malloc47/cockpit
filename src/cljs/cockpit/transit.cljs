(ns cockpit.transit
  (:require
   [ajax.core :as ajax]
   [cljs-time.coerce :as time-coerce]
   [cljs-time.core :as time]
   [clojure.set :refer [difference]]
   [clojure.string :as str]
   [cockpit.clock :as clock]
   [cockpit.config :as config]
   [cockpit.db :as db]
   [cockpit.events :as events]
   [cockpit.utils :refer [safe-interval format-interval]]
   [day8.re-frame.http-fx]
   [plumbing.core :refer [map-vals]]
   [re-frame.core :as re-frame]))

;;; Fallback flow specific to MTA

(defn split-stop-id
  [stop-id]
  (let [[_ agency-id stop-alias line direction]
        (re-find #"(\w+):((\w)\w+)(\w)" stop-id)]
    {:agency-id  agency-id
     :stop-alias stop-alias
     :line       line
     :direction  direction}))

(defn fallback->route-ids
  [fallback]
  (->> fallback
       ((juxt :direction1 :direction2))
       (mapcat :times)
       (map :route)
       distinct
       (map (partial str config/fallback-agency ":"))))

(def headsign->direction
  {"Downtown" "S"
   "Uptown" "N"})

(def direction->direction-id
  {"S" "1"
   "N" "0"})

(defn fallback->stoptimes
  [{:keys [agency-id] :as stop} {:keys [direction1 direction2]}]
  (->> [direction1 direction2]
       (mapcat
        (fn [{:keys [name times]}]
          (let [direction (get headsign->direction name)
                ;; reconstruct this id because we fetched both and
                ;; need to append the direction to the ID
                stop-id (str (->> stop :stop-id first drop-last
                                  (str/join ""))
                             direction)]
            (map (fn [{:keys [route minutes]}]
                   {:minutes        minutes
                    :stop-id        stop-id
                    :route-id       (str agency-id ":" route)
                    :realtime-state "SCHEDULED"
                    :realtime?      true
                    :direction-id   (get direction->direction-id direction)})
                 times))))))

(re-frame/reg-event-fx
 ::fetch-stop-times-fallback
 (fn [_ [_ {:keys [stop-id] :as stop}]]
   (let [{:keys [stop-alias line]}
         (split-stop-id (cond-> stop-id (sequential? stop-id) first))]
     {:http-xhrio
      {:method          :get
       :uri             (str config/fallback-uri
                             line
                             "/"
                             stop-alias)
       :response-format (ajax/json-response-format {:keywords? true})
       ;; :id will be an array and the payload will be duplicated in the
       ;; db which will match the shape of the OTP-based transit query
       ;; which is separated by direction
       :on-success      [::persist-stop-times
                         [:transit-fallback :stop-times stop]]
       :on-failure      [::events/http-fail
                         [:transit-fallback :stop-times stop]]}})))

(re-frame/reg-sub
 ::stop-times-raw-fallback
 (fn [db _]
   (-> db :transit-fallback :stop-times)))

(re-frame/reg-sub
 ::stop-times-fallback
 :<- [::stop-times-raw-fallback]
 (fn [stop-times _]
   (mapcat (fn [[stop times]]
             (fallback->stoptimes stop times))
           stop-times)))

;;; Open Trip Planner (OTP) index API
;;;
;;; http://dev.opentripplanner.org/apidoc/1.4.0/resource_IndexAPI.html
;;;
;;; This particular client is written for an API that is proxied
;;; through a gateway that requires an apikey parameter that is not
;;; part of the offically-documented API.

(defn gtfs->route-id [{{route-id :id} :route}] route-id)

(defn gtfs->stoptimes
  [now {:keys [times route]}]
  (->> times
       (map #(assoc % :route route))
       (map
        (fn [{time           :realtimeDeparture
              day            :serviceDay
              stop-id        :stopId
              {route-id :id} :route
              rts            :realtimeState
              realtime?      :realtime
              direction-id   :directionId}]
          {:minutes        (-> time (+ day) (* 1e3)
                               time-coerce/from-long
                               (->> (safe-interval now))
                               time/in-seconds
                               (/ 60)
                               js/Math.ceil)
           :stop-id        stop-id
           :route-id       route-id
           :realtime-state rts
           :realtime?      realtime?
           :direction-id   direction-id}))))

(def otp-request
  {:method          :get
   :response-format (ajax/json-response-format {:keywords? true})})

;;; Events

(re-frame/reg-event-fx
 ::fetch-stop-times
 (fn [_ [_ {:keys [stop-id] :as stop}]]
   {:http-xhrio
    (merge
     otp-request
     {:uri             (str config/otp-uri
                            "/routers/default/index/stops/"
                            stop-id
                            "/stoptimes")
      :params          {:apikey    config/otp-api-key
                        #_#_:timeRange 7200}
      :on-success      [::persist-stop-times [:transit :stop-times stop]]
      :on-failure      [::events/http-fail [:transit :stop-times stop]]})}))

(re-frame/reg-event-fx
 ::fetch-route
 (fn [_ [_ route-id]]
   {:http-xhrio
    (merge
     otp-request
     {:uri             (str config/otp-uri
                            "/routers/default/index/routes/"
                            route-id
                            "/")
      :params          {:apikey config/otp-api-key}
      :on-success      [::events/http-success [:transit :routes route-id]]
      :on-failure      [::events/http-fail [:transit :routes route-id]]})}))

(re-frame/reg-event-fx
 ::fetch-stop
 (fn [_ [_ {:keys [stop-id] :as stop}]]
   {:http-xhrio
    (merge
     otp-request
     {:uri             (str config/otp-uri
                            "/routers/default/index/stops/"
                            stop-id
                            "/")
      :params          {:apikey config/otp-api-key}
      :on-success      [::events/http-success
                        [:transit :stops stop-id]]
      :on-failure      [::events/http-fail
                        [:transit :stops stop-id]]})}))

;;; TODO: maybe consider one of
;;; https://github.com/Day8/re-frame-async-flow-fx
;;; https://github.com/day8/re-frame-forward-events-fx
(re-frame/reg-event-fx
 ::persist-stop-times
 (fn [{:keys [db]} [_ key-path stop-times]]
   (let [existing-route-ids (-> db :transit :routes keys set)
         ;; make this function work on the normal GTFS payload
         ;; (vector) or the fallback payload (map)
         new-route-ids      (set (if (sequential? stop-times)
                                   (map gtfs->route-id stop-times)
                                   (fallback->route-ids stop-times)))
         ;; diff what is in the DB with the newly-seen routes so we
         ;; only fetch them once
         route-ids          (->> (difference new-route-ids
                                             existing-route-ids)
                                 (remove nil?))
         stop-id (-> key-path last :stop-id)]
     { ;; attach the raw requests to the db
      :db (-> db
              (assoc-in key-path stop-times)
              (events/assoc-in-all [:transit :update-times stop-id] (js/Date.)))
      ;; fire requests for the routes listed in the payload
      :dispatch-n (map (fn [route-id]
                         [::fetch-route route-id])
                       route-ids)})))

(re-frame/reg-event-db
 ::clear
 (fn [db _]
   (-> db
       (assoc :transit (:transit db/default-db))
       (assoc :transit-fallback (:transit-fallback db/default-db)))))

(defn generate-stop-time-events
  [config]
  (->> config
       (filter (comp not :fallback?))
       (map (fn [stop]
              [::fetch-stop-times stop]))))

(defn generate-fallback-stop-time-events
  [config]
  (->> config
       (filter :fallback?)
       ;; create a key without the direction for grouping
       (map #(assoc % :stop (->> %
                                 :stop-id
                                 drop-last
                                 (str/join ""))
                    :stop-id (list (:stop-id %))))
       ;; ignore the other keys
       (group-by (juxt :agency-id :stop))
       vals
       (map (partial
             reduce
             (fn [a b]
               (let [c (merge a b)]
                 (-> c
                     ;; merge :id key into a list of ids
                     (assoc :stop-id (concat (:stop-id a) (:stop-id b)))
                     (dissoc :stop))))))
       (map (fn [stop]
              [::fetch-stop-times-fallback stop]))))

(defn generate-stop-events
  [config]
  (map (fn [stop]
         [::fetch-stop stop])
       config))

(defn generate-events
  [config]
  (concat
   (generate-stop-time-events config)
   (generate-fallback-stop-time-events config)
   (generate-stop-events config)))

;;; Subscriptions

(re-frame/reg-sub
 ::stop-times-raw
 (fn [db _]
   (-> db :transit :stop-times)))

(re-frame/reg-sub
 ::stop-times-update-times
 (fn [db _]
   (-> db :transit :update-times)))

(re-frame/reg-sub
 ::routes-raw
 (fn [db _]
   (-> db :transit :routes)))

(re-frame/reg-sub
 ::stops-raw
 (fn [db _]
   (-> db :transit :stops)))

(re-frame/reg-sub
 ::stops
 :<- [::stops-raw]
 (fn [stops _]
   (->> stops
        (map-vals
         (fn [{:keys [name] stop-id :id}]
           (let [{:keys [direction-id sort-override]}
                 (->> config/transit-stop-whitelist
                      (filter (comp (partial = stop-id) :stop-id))
                      first)]
             {:name          name
              :stop-id       stop-id
              :direction-id  direction-id
              :sort-override sort-override})))
        (into {}))))

(re-frame/reg-sub
 ::routes
 :<- [::routes-raw]
 (fn [routes _]
   (->> routes
        (map-vals
         (fn [{route-id    :id
               description :desc
               color       :color
               text-color  :textColor
               short-name  :shortName
               long-name   :longName
               sort-order  :sortOrder}]
           {:route-id    route-id
            :description description
            :color       color
            :text-color  text-color
            :short-name  short-name
            :long-name   long-name
            :sort-order  sort-order}))
        (into {}))))

(re-frame/reg-sub
 ::stop-times
 :<- [::stop-times-raw]
 :<- [::clock/clock]
 (fn [[stop-times clock] _]
   (let [now (time-coerce/from-date clock)]
     (->> stop-times vals (apply concat)
          (mapcat (partial gtfs->stoptimes now))))))

(re-frame/reg-sub
 ::stop-times-update-interval
 :<- [::clock/clock]
 :<- [::stop-times-update-times]
 (fn [[clock update-times] _]
   (when update-times
     (some->> update-times
              vals
              (map (fn [update-time]
                     (safe-interval
                      (time-coerce/from-date update-time)
                      (time-coerce/from-date clock))))
              (apply max-key time/in-minutes)
              format-interval))))

(re-frame/reg-sub
 ::stop-times-joined
 :<- [::stop-times]
 ;; Injects the fallback into the main transit subscription flow
 :<- [::stop-times-fallback]
 (fn [stop-time-groups _]
   (apply concat stop-time-groups)))

(defn roll-up-route
  "This rolls up multiple routes from individual stops into a single
  aggregate route. This is useful for cases where we don't care which
  particular route we hop on at a particular station (say all routes
  make local stops). Aggregating lets us display all routes in the
  station as individual times in a single aggregate route."
  [stop-times]
  (-> (reduce (fn [route {route2 :route}]
                (reduce (fn [m k]
                          (update m k conj (get route2 k)))
                        route
                        (keys route)))
              {:route-id    #{}
               :description #{}
               :color       #{}
               :text-color  #{}
               :short-name  #{}
               :long-name   #{}
               :sort-order  #{}}
              stop-times)
      (update :color first)
      (update :text-color (partial apply max))
      (update :short-name (comp (partial str/join "/") sort))
      (update :sort-order (partial apply min))))

(def stop-times-view-filter
  (-> (every-pred nat-int? (partial > 60))
      (comp :minutes)))

(re-frame/reg-sub
 ::stop-times-processed
 :<- [::stop-times-joined]
 :<- [::routes]
 :<- [::stops]
 (fn [[stop-times routes stops] _]
   (->> stop-times
        (filter stop-times-view-filter) ; view logic
        (map (fn [{:keys [stop-id route-id] :as stop-time}]
               (-> stop-time
                   (assoc :stop (get stops stop-id))
                   (assoc :route (get routes route-id)))))
        ;; Make this an inner join
        (filter (every-pred :stop :route))
        ;; Handle the grouping by colored routes or something similar
        (group-by #(select-keys % [:stop]))
        (map (fn [[k v]]
               [(assoc k :route (roll-up-route v))
                v]))
        ;; more view logic here
        (map-vals #(->> %
                        (filter
                         (fn [{:keys [direction-id]
                               {stop-direction-id :direction-id} :stop}]
                           (or
                            (= direction-id stop-direction-id)
                            (nil? stop-direction-id))))
                        (sort-by :minutes)
                        (take 4)))
        (sort-by (juxt (comp :sort-override :stop first)
                       (comp :sort-order :route first)
                       (comp :stop-id :stop first))))))
