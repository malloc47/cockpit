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

(defn date->str
  [date]
  (time-format/unparse (time-format/formatters :date) date))

(defn prev-day
  [date]
  (->> 1 time/days (time/minus date)))

;;; Events

(re-frame/reg-event-fx
 ::persist-stocks
 (fn [{:keys [db]} [_ symbol result]]
   (let [prev  (->> result (map :date) distinct first
                    time-format/parse prev-day date->str)
         cache (-> db :stocks (get (keyword symbol))
                   :previous-close :date)]
     {:db (-> db
              (assoc-in [:stocks (keyword symbol) :data] result)
              (assoc-in [:stocks :update-time] (js/Date.)))
      :dispatch-n (if (= cache prev) (list) (list [::fetch-prev-close symbol]))})))

(re-frame/reg-event-db
 ::persist-prev-close
 (fn [db [_ symbol result]]
   ;; TODO: This will grow without bound until the page is closed
   (assoc-in db [:stocks (keyword symbol) :previous-close] result)))

(re-frame/reg-event-fx
 ::fetch-stocks
 (fn [_ [_ symbol]]
   {:http-xhrio
    {:method          :get
     :uri             (str "https://cloud.iexapis.com/stable/stock/"
                           symbol "/"
                           "intraday-prices")
     :params          {:token config/iex-api-key}
     :response-format (ajax/json-response-format {:keywords? true})
     :on-success      [::persist-stocks symbol]
     ;; TODO: this nukes the whole payload even if one of the queries
     ;; is successful
     :on-failure      [::events/http-fail [:stocks]]}}))

(re-frame/reg-event-fx
 ::fetch-prev-close
 (fn [_ [_ symbol]]
   {:http-xhrio
    {:method          :get
     :uri             (str "https://cloud.iexapis.com/stable/stock/"
                           symbol "/"
                           "previous")
     :params          {:token config/iex-api-key}
     :response-format (ajax/json-response-format {:keywords? true})
     :on-success      [::persist-prev-close symbol]
     ;; TODO: this nukes the whole payload even if one of the queries
     ;; is successful
     :on-failure      [::events/http-fail [:stocks]]}}))

;;; Subs

(defn convert-iex-to-sparkline
  [iex-list]
  (let [intraday
        (->> iex-list
             rest
             (remove (comp zero? :numberOfTrades))
             (map (fn [{:keys [date minute] :as iex}]
                    (->> (str date " " minute)
                         (.parse js/Date)
                         (assoc iex :timestamp))))
             (sort-by :timestamp))]
    (concat [(-> iex-list first :close)]
            (map :close intraday))))

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
        (remove nil?)
        (map (fn [[symbol {:keys [data previous-close]}]]
               [symbol (convert-iex-to-sparkline
                        (concat [previous-close] data))]))
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
