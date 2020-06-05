(ns cockpit.transit
  (:require
   [ajax.core :as ajax]
   [cljs-time.coerce :as time-coerce]
   [cljs-time.core :as time]
   [clojure.set :refer [difference map-invert]]
   [clojure.string :as str]
   [cockpit.config :as config]
   [cockpit.events :as events]
   [cockpit.subs :as subs]
   [day8.re-frame.http-fx]
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

(defn stop->gtfs-id
  [{:keys [agency-id stop-id direction]}]
  (str agency-id ":" stop-id direction))

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

(defn fallback->stoptimes
  [{:keys [agency-id] :as stop} {:keys [direction1 direction2]}]
  (->> [direction1 direction2]
       (mapcat
        (fn [{:keys [name times]}]
          (let [direction (get headsign->direction name)
                ;; reconstruct this id because
                stop-id (str (->> stop :stop-id drop-last
                                  (str/join ""))
                             direction)]
            (map (fn [{:keys [route minutes]}]
                   {:minutes        minutes
                    :stop-id        stop-id
                    :route-id       (str agency-id ":" route)
                    :realtime-state "SCHEDULED"
                    :realtime?      true})
                 times))))))

(re-frame/reg-event-fx
 ::fetch-stop-times-fallback
 (fn [_ [_ {:keys [stop-id] :as stop}]]
   (let [{:keys [stop-alias line]} (split-stop-id stop-id)]
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
       :on-success      [::persist-stop-times [:transit-fallback :stop-times stop]]
       :on-failure      [::events/http-fail :transit-fallback]}})))

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

;;;

(defn safe-interval
  "The local clock and the OTP instance clock are not guaranteed to be
  in sync, and in practice the OTP instance provides times ahead of
  local clock. Instead of blowing up, this swallows these errors."
  [a b]
  (try
    (time/interval a b)
    (catch js/Object e
      (time/interval b b))))

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
              realtime?      :realtime}]
          {:minutes        (-> time (+ day) (* 1e3)
                               time-coerce/from-long
                               (->> (safe-interval now))
                               time/in-seconds
                               (/ 60)
                               js/Math.ceil)
           :stop-id        stop-id
           :route-id       route-id
           :realtime-state rts
           :realtime?      realtime?}))))

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
                        :timeRange 7200}
      :on-success      [::persist-stop-times [:transit :stop-times stop]]
      :on-failure      [::events/http-fail :transit]})}))

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
      ;; TODO: this nukes the whole payload even if one of the queries
      ;; is successful
      :on-failure      [::events/http-fail :transit]})}))

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
      :on-failure      [::events/http-fail :transit]})}))

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
                                 (remove nil?))]
     { ;; attach the raw requests to the db
      :db (assoc-in db key-path stop-times)
      ;; fire requests for the routes listed in the payload
      :dispatch-n (map (fn [route-id]
                         [::fetch-route route-id])
                       route-ids)})))

;;; Subscriptions

(re-frame/reg-sub
 ::stop-times-raw
 (fn [db _]
   (-> db :transit :stop-times)))

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
        (map (fn [[k {:keys [name] stop-id :id}]]
               [k {:name    name
                   :stop-id stop-id}]))
        (into {}))))

(re-frame/reg-sub
 ::routes
 :<- [::routes-raw]
 (fn [routes _]
   (->> routes
        (map (fn [[k {description :desc
                      color       :color
                      text-color  :textColor
                      short-name  :shortName
                      long-name   :longName
                      sort-order  :sortOrder}]]
               [k {:description description
                   :color       color
                   :text-color  text-color
                   :short-name  short-name
                   :long-name   long-name
                   :sort-order  sort-order}]))
        (into {}))))

(re-frame/reg-sub
 ::stop-times
 :<- [::stop-times-raw]
 :<- [::subs/clock]
 (fn [[stop-times clock] _]
   (let [now (time-coerce/from-date clock)]
     (->> stop-times vals (apply concat)
          (mapcat (partial gtfs->stoptimes now))))))

(re-frame/reg-sub
 ::stop-times-joined
 :<- [::stop-times]
 ;; Injects the fallback into the main transit subscription flow
 :<- [::stop-times-fallback]
 (fn [stop-time-groups _]
   (apply concat stop-time-groups)))

(re-frame/reg-sub
 ::stop-times-filtered
 :<- [::stop-times-joined]
 (fn [stop-times [_ {:keys [stop-id]}]]
   (->> stop-times
        (filter #(= stop-id (:stop-id %)))
        (map :minutes)
        (filter pos?)
        (filter (partial > 90))
        sort
        (take 4))))

(re-frame/reg-sub
 ::stop-times-processed
 :<- [::stop-times-joined]
 :<- [::routes]
 :<- [::stops]
 (fn [[stop-times routes stops] _]
   (->> stop-times
        (map (fn [{:keys [stop-id route-id] :as stop-time}]
               (-> stop-time
                   (assoc :stop (get stops stop-id))
                   (assoc :route (get routes route-id)))))
        (group-by #(select-keys % [:route :stop]))
        (sort-by (juxt (comp :sort-order :route first)
                       (comp :stop-id :stop first)))
        (map (fn [[k v]] [k (sort-by :minutes v)]))
        (into {}))))
