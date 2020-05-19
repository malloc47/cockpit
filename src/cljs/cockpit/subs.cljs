(ns cockpit.subs
  (:require
   [re-frame.core :as re-frame]
   [cljs-time.core :as time]
   [cljs-time.format :as time-format]
   [cljs-time.coerce :as time-coerce]))

(defn db->date-sub
  [format-map]
  (fn [db _]
   (-> db
       :time
       (.toLocaleTimeString [] (clj->js format-map)))))

(re-frame/reg-sub
 ::time
 (db->date-sub {:hour "numeric" :minute "numeric" :hour12 true}))

(re-frame/reg-sub
 ::time-pt
 (db->date-sub
  {:hour "numeric" :minute "numeric"
   :hour12 true :timeZone "America/Los_Angeles"}))

(re-frame/reg-sub
 ::time-ct
 (db->date-sub
  {:hour "numeric" :minute "numeric"
   :hour12 true :timeZone "America/Chicago"}))

(re-frame/reg-sub
 ::day
 (fn [db _]
   (-> db
       :time
       (.toLocaleDateString
        [] #js {:weekday "long" :month "long" :day "numeric"}))))

(defn date->str
  [date]
  (time-format/unparse (time-format/formatters :date) date))

(defn prev-day
  [date]
  (->> 1 time/days (time/minus date)))

(defn epoch->local-date
  [epoch]
  (-> epoch
      (* 1000)
      time-coerce/from-long
      time/to-default-time-zone))

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
            prev-close (->> full-list
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
   (->> db
        :stocks
        (map (fn [[symbol api-result]]
               [symbol (convert-alpha-vantage-to-sparkline api-result)]))
        (into {}))))

(re-frame/reg-sub
 ::weather
 (fn [db _]
   (:weather db)))

(re-frame/reg-sub
 ::sun
 (fn [{{{:keys [sunrise sunset]} :current} :weather} _]
   {:sunrise (-> sunrise epoch->local-date .toUsTimeString)
    :sunset  (-> sunset epoch->local-date .toUsTimeString)}))
