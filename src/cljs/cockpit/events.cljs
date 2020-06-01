(ns cockpit.events
  (:require
   [re-frame.core :as re-frame]
   [cockpit.db :as db]
   [cockpit.config :as config]
   [clojure.string :as str]
   [day8.re-frame.tracing :refer-macros [fn-traced]]
   [day8.re-frame.http-fx]
   [ajax.core :as ajax]))


(re-frame/reg-event-db
 ::initialize-db
 (fn-traced [_ _]
            db/default-db))

(re-frame/reg-event-db
 ::timer
 (fn [db [_ new-time]]
   (assoc db :clock (js/Date.))))

(re-frame/reg-event-db
 ::stocks
 (fn [db [_ result]]
   (let [symbol (-> result
                    (get "Meta Data")
                    (get "2. Symbol"))]
     (assoc-in db [:stocks (keyword symbol)] result))))

(defn explode-nested
  "Explode the values from nested seqs into multiple collections"
  [coll]
  (reduce (fn [coll el]
            (if (sequential? el)
              (for [c coll e el]
                (conj c e))
              (map (fn [c] (conj c el)) coll)))
          [[]]
          coll))

(re-frame/reg-event-db
 ::http-success
 (fn [db [_ key-path result]]
   (if (sequential? key-path)
     (reduce (fn [db key-path]
               (assoc-in db key-path result))
             db
             (explode-nested key-path))
     (assoc-in db [key-path] result))))

(re-frame/reg-event-db
 ::http-fail
 (fn [db [_ [_ key & _]]]
   (assoc db key {})))

(re-frame/reg-event-fx
 ::fetch-stocks
 (fn [_ [_ symbol]]
   {:http-xhrio
    {:method          :get
     :uri             "https://www.alphavantage.co/query"
     :params          {:function   "TIME_SERIES_INTRADAY"
                       :symbol     symbol
                       :interval   "5min"
                       :outputsize "compact"
                       :apikey     config/alpha-vantage-api-key}
     :response-format (ajax/json-response-format {:keywords? false})
     :on-success      [::stocks]
     ;; TODO: this nukes the whole payload even if one of the queries
     ;; is successful
     :on-failure      [::http-fail :stocks]}}))

(re-frame/reg-event-fx
 ::fetch-weather
 (fn [_ _]
   {:http-xhrio
    {:method :get
     :uri    "http://api.openweathermap.org/data/2.5/onecall"
     :params {:lat   config/lat
              :lon   config/lon
              :units "imperial"
              :appid config/open-weather-api-key}
     :response-format (ajax/json-response-format {:keywords? true})
     :on-success      [::http-success :weather]
     :on-failure      [::http-fail :weather]}}))

(re-frame/reg-event-fx
 ::fetch-covid
 (fn [_ _]
   {:http-xhrio
    {:method :get
     :uri    "https://data.cityofnewyork.us/resource/rc75-m7u3.json"
     :response-format (ajax/json-response-format {:keywords? true})
     :on-success      [::http-success :covid]
     :on-failure      [::http-fail :covid]}}))

(defn stop->gtfs-id
  [{:keys [agency-id stop-id direction]}]
  (str agency-id ":" stop-id direction))

(re-frame/reg-event-fx
 ::fetch-transit-stop
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
     :on-success      [::http-success [:transit id :stop-times]]
     :on-failure      [::http-fail :transit]}}))

(re-frame/reg-event-fx
 ::fetch-transit-fallback
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
     :on-success      [::http-success [:transit-fallback id :stop-times]]
     :on-failure      [::http-fail :transit-fallback]}}))

#_(re-frame/reg-event-fx
 ::fetch-transit-route
 (fn [_ [_ route-id alias]]
   {:http-xhrio
    {:method          :get
     :uri             (str config/otp-uri
                           "/routers/default/index/routes/"
                           route-id
                           "/")
     :params          {:apikey config/otp-api-key}
     :response-format (ajax/json-response-format {:keywords? true})
     :on-success      [::http-success [:transit alias :route]]
     ;; TODO: this nukes the whole payload even if one of the queries
     ;; is successful
     :on-failure      [::http-fail :transit]}}))
