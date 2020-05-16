(ns cockpit.subs
  (:require
   [re-frame.core :as re-frame]
   [cljs-time.core :as time]
   [cljs-time.format :as time-format]))

(re-frame/reg-sub
 ::time
 (fn [db _]
   (-> db
       :time
       (.toLocaleTimeString [] #js {:hour "numeric" :minute "numeric" :hour12 true}))))

(re-frame/reg-sub
 ::day
 (fn [db _]
   (-> db
       :time
       (.toLocaleDateString [] #js {:weekday "long" :month "long" :day "numeric"}))))

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
    ;; until we have non-empty data. This handles weekend/holiday
    ;; gaps, and past-midnight-but-before-trading times.
    (loop [today      (time/today)
           yesterday  (prev-day today)]
      (let [today-data (->> full-list
                            (filter #(-> % :date (= (date->str today))))
                            (sort-by :time))
            prev-close (->> full-list
                            (filter #(-> % :date (= (date->str yesterday))))
                            (apply max-key :time))]
        (if (not-empty today-data)
          (->> (concat [prev-close] today-data)
               (map :value)
               vec)
          (recur (prev-day today) (prev-day yesterday)))))))

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
