(ns cockpit.transit
  (:require
   [ajax.core :as ajax]
   [cljs-time.coerce :as time-coerce]
   [cljs-time.core :as time]
   [clojure.set :refer [difference]]
   [cockpit.config :as config]
   [cockpit.events :as events]
   [cockpit.subs :as subs]
   [day8.re-frame.http-fx]
   [re-frame.core :as re-frame]))

;;; Utils

(defn stop->gtfs-id
  [{:keys [agency-id stop-id direction]}]
  (str agency-id ":" stop-id direction))

(defn gtfs->route-id [{{route-id :id} :route}] route-id)

(defn fallback->route-ids
  [fallback]
  (->> fallback
       ((juxt :direction1 :direction2))
       (mapcat :times)
       (map :route)
       distinct
       (map (partial str config/fallback-agency ":"))))

(defn safe-interval
  "The local clock and the OTP instance clock are not guaranteed to be
  in sync, and in practice the OTP instance provides times ahead of
  local clock. Instead of blowing up, this swallows these errors."
  [a b]
  (try
    (time/interval a b)
    (catch js/Object e
      (time/interval b b))))

;;; Events

(re-frame/reg-event-fx
 ::fetch-stop-times
 (fn [_ [_ {:keys [id] :as stop}]]
   {:http-xhrio
    {:method          :get
     :uri             (str config/otp-uri
                           "/routers/default/index/stops/"
                           (stop->gtfs-id stop)
                           "/stoptimes")
     :params          {:apikey    config/otp-api-key
                       :timeRange 7200}
     :response-format (ajax/json-response-format {:keywords? true})
     :on-success      [::persist-stop-times [:transit :stop-times id]]
     :on-failure      [::events/http-fail :transit]}}))

(re-frame/reg-event-fx
 ::fetch-stop-times-fallback
 (fn [_ [_ {:keys [id stop-id]}]]
   {:http-xhrio
    {:method          :get
     :uri             (str config/fallback-uri
                           (first stop-id)
                           "/"
                           stop-id)
     :response-format (ajax/json-response-format {:keywords? true})
     ;; :id will be an array and the payload will be duplicated in the
     ;; db which will match the shape of the OTP-based transit query
     ;; which is separated by direction
     :on-success      [::persist-stop-times [:transit-fallback :stop-times id]]
     :on-failure      [::events/http-fail :transit-fallback]}}))


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
      :db (events/assoc-in-all db key-path stop-times)
      ;; fire requests for the routes listed in the payload
      :dispatch-n (map (fn [route-id]
                         [::fetch-route route-id])
                       route-ids)})))

(re-frame/reg-event-fx
 ::fetch-route
 (fn [_ [_ route-id]]
   {:http-xhrio
    {:method          :get
     :uri             (str config/otp-uri
                           "/routers/default/index/routes/"
                           route-id
                           "/")
     :params          {:apikey config/otp-api-key}
     :response-format (ajax/json-response-format {:keywords? true})
     :on-success      [::events/http-success [:transit :routes route-id]]
     ;; TODO: this nukes the whole payload even if one of the queries
     ;; is successful
     :on-failure      [::events/http-fail :transit]}}))

;;; Subscriptions

(re-frame/reg-sub
 ::stop-times-raw
 (fn [db _]
   (-> db :transit :stop-times)))

(re-frame/reg-sub
 ::stop-times-raw-fallback
 (fn [db _]
   (-> db :transit-fallback :stop-times)))

(defn gtfs->stoptimes
  ""
  [now
   {time           :realtimeDeparture
    day            :serviceDay
    live           :realtimeState
    {route-id :id} :route}]
  (let [minutes (-> time (+ day) (* 1e3)
                    time-coerce/from-long
                    (->> (safe-interval now))
                    time/in-seconds
                    (/ 60)
                    js/Math.ceil)]
    {:minutes  minutes
     :route-id route-id}
    minutes))

(re-frame/reg-sub
 ::stop-times
 :<- [::stop-times-raw]
 :<- [::subs/clock]
 (fn [[stop-times clock] [_ {:keys [id]}]]
   (let [now (time-coerce/from-date clock)]
     (->> stop-times id
          (mapcat :times)
          (map (partial gtfs->stoptimes now))
          (filter pos?)
          (filter (partial > 90))
          sort
          (take 4)))))

(defn direction->name [direction]
  (condp = direction
    "S" "Downtown"
    "N" "Uptown"))

(re-frame/reg-sub
 ::stop-times-fallback
 :<- [::stop-times-raw-fallback]
 (fn [stop-times [_ {:keys [direction id]}]]
   (let [{:keys [direction1 direction2]}
         (->> stop-times id)]
     (->> [direction1 direction2]
          (filter #(= (:name %) (direction->name direction)))
          (mapcat (comp (partial map :minutes) :times))
          (filter pos?)
          sort
          (take 4)))))
