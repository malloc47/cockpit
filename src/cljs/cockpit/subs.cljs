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

;;; These are all L2 subs but should be converted to L3
;;; https://github.com/day8/re-frame/blob/master/docs/subscriptions.md
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

(re-frame/reg-sub
 ::covid
 (fn [db _]
   (->> db :covid
        (mapcat
         (fn [row]
           (let [base-row {:date (:date_of_interest row)}]
             [(merge base-row {:y (:case_count row) :type "cases"})
              (merge base-row {:y (:hospitalized_count row) :type "hospitalized"})
              (merge base-row {:y (:death_count row) :type "deaths"})])))
        seq)))

(defn safe-interval [a b]
  (try
    (time/interval a b)
    (catch js/Object e
      (js/console.log (str "Interval exception swallowed " a " " b))
      (time/interval b b))))

(re-frame/reg-sub
 ::transit-stops
 (fn [db [_ alias]]
   (let [now  (time/now)
         data (->> db :transit alias :stop-times)]
     (->> db :transit alias :stop-times
          (mapcat :times)
          (map (fn [{time :realtimeDeparture
                     day  :serviceDay
                     live :realtimeState}]
                 (->> time (+ day) (* 1e3)
                      time-coerce/from-long
                      (safe-interval now)
                      time/in-minutes)))
          (filter pos?)
          (filter (partial > 90))
          sort
          (take 4)))))

(defn alias->direction [alias]
  (condp = (-> alias name last)
    "S" "Downtown"
    "N" "Uptown"))

(re-frame/reg-sub
 ::transit-stops-fallback
 (fn [db [_ alias]]
   (let [{:keys [direction1 direction2]}
         (->> db :transit-fallback alias :stop-times)]
     (->> [direction1 direction2]
          (filter #(= (:name %) (alias->direction alias)))
          (mapcat (comp (partial map :minutes) :times))
          (filter pos?)
          sort
          (take 4)))))
