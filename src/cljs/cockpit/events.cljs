(ns cockpit.events
  (:require
   [re-frame.core :as re-frame]
   [cockpit.db :as db]
   [cockpit.config :as config]
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
   (assoc db :time (js/Date.))))

(re-frame/reg-event-db
 ::stocks
 (fn [db [_ result]]
   (let [symbol (-> result
                    (get "Meta Data")
                    (get "2. Symbol"))]
     (assoc-in db [:stocks (keyword symbol)] result))))

(re-frame/reg-event-db
 ::stocks-fail
 (fn [db [_ result]]
   (assoc db :stocks {})))

(re-frame/reg-event-db
 ::weather
 (fn [db [_ result]]
   (assoc db :weather result)))

(re-frame/reg-event-db
 ::weather-fail
 (fn [db [_ result]]
   (assoc db :weather {})))

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
     :on-failure      [::stocks-fail]}}))

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
     :on-success      [::weather]
     :on-failure      [::weather-fail]}}))
