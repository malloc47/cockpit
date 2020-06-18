(ns cockpit.stocks
  (:require
   [ajax.core :as ajax]
   [cljs-time.coerce :as time-coerce]
   [cljs-time.core :as time]
   [cljs-time.format :as time-format]
   [cockpit.config :as config]
   [cockpit.events :as events]
   [cockpit.clock :as clock]
   [cockpit.utils :refer [safe-interval format-interval]]
   [day8.re-frame.http-fx]
   [re-frame.core :as re-frame]))

;;; Events

(re-frame/reg-event-db
 ::stocks
 (fn [db [_ result]]
   (let [symbol (-> result
                    (get "Meta Data")
                    (get "2. Symbol"))]
     (-> db
         (assoc-in [:stocks (keyword symbol)] result)
         (assoc-in [:stocks :update-time] (js/Date.))))))

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
     :on-failure      [::events/http-fail [:stocks]]}}))

;;; Subs

(defn date->str
  [date]
  (time-format/unparse (time-format/formatters :date) date))

(defn prev-day
  [date]
  (->> 1 time/days (time/minus date)))

(defn convert-alpha-vantage-to-sparkline
  [av]
  (let [full-list
        (->> (get av "Time Series (5min)")
             (map (fn [[k v]]
                    {:date (subs k 0 10)
                     :time (.parse js/Date k)
                     :value (js/parseFloat (get v "4. close"))})))]
    ;; Start with today and move both backwards by a day at a time
    ;; until we find the last trading day. This handles
    ;; weekend/holiday gaps, and past-midnight-but-before-open dates.
    (loop [trading-day (time/today)]
      (let [trading-data (->> full-list
                              (filter #(-> % :date (= (date->str trading-day))))
                              (sort-by :time))
            prev-close   (->> full-list
                              (remove #(-> % :date (= (date->str trading-day))))
                              (apply max-key :time))]
        (if (not-empty trading-data)
          (->> (concat [prev-close] trading-data)
               (map :value)
               vec)
          (recur (prev-day trading-day)))))))

(re-frame/reg-sub
 ::stocks
 (fn [db _]
   (:stocks db)))

(re-frame/reg-sub
 ::stocks-sparkline
 :<- [::stocks]
 (fn [stocks _]
   ;; TODO: a bit annoying to have to dissoc this
   (->> (dissoc stocks :update-time)
        (map (fn [[symbol api-result]]
               [symbol (convert-alpha-vantage-to-sparkline api-result)]))
        (into {}))))

(re-frame/reg-sub
 ::stocks-update-time
 :<- [::clock/clock]
 :<- [::stocks]
 (fn [[clock {:keys [update-time]}] _]
   (when update-time
     (format-interval
      (safe-interval
       (time-coerce/from-date update-time)
       (time-coerce/from-date clock))))))
