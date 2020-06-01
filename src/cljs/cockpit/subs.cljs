(ns cockpit.subs
  (:require
   [re-frame.core :as re-frame]
   [cljs-time.core :as time]
   [cljs-time.format :as time-format]
   [cljs-time.coerce :as time-coerce]))

(defn db->date-sub
  [format-map]
  (fn [clock _]
   (.toLocaleTimeString clock [] (clj->js format-map))))

(re-frame/reg-sub
 ::clock
 (fn [db _]
   (:clock db)))

(re-frame/reg-sub
 ::time
 :<- [::clock]
 (db->date-sub {:hour "numeric" :minute "numeric" :hour12 true}))

(re-frame/reg-sub
 ::time-pt
 :<- [::clock]
 (db->date-sub
  {:hour "numeric" :minute "numeric"
   :hour12 true :timeZone "America/Los_Angeles"}))

(re-frame/reg-sub
 ::time-ct
 :<- [::clock]
 (db->date-sub
  {:hour "numeric" :minute "numeric"
   :hour12 true :timeZone "America/Chicago"}))

(re-frame/reg-sub
 ::day
 :<- [::clock]
 (fn [clock _]
   (.toLocaleDateString
    clock
    [] #js {:weekday "long" :month "long" :day "numeric"})))

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
   (:stocks db)))

(re-frame/reg-sub
 ::stocks-sparkline
 :<- [::stocks]
 (fn [stocks _]
   (->> stocks
        (map (fn [[symbol api-result]]
               [symbol (convert-alpha-vantage-to-sparkline api-result)]))
        (into {}))))

(re-frame/reg-sub
 ::weather
 (fn [db _]
   (:weather db)))

(re-frame/reg-sub
 ::sun
 :<- [::weather]
 (fn [{{:keys [sunrise sunset]} :current} _]
   {:sunrise (-> sunrise epoch->local-date .toUsTimeString)
    :sunset  (-> sunset epoch->local-date .toUsTimeString)}))

(re-frame/reg-sub
 ::covid
 (fn [db _]
   (:covid db)))

(re-frame/reg-sub
 ::covid-rows
 :<- [::covid]
 (fn [covid _]
   (->> covid
        butlast
        (mapcat
         (fn [row]
           (let [base-row {:date (:date_of_interest row)}]
             [(merge base-row {:y (:case_count row) :type "cases"})
              (merge base-row {:y (:hospitalized_count row) :type "hospitalized"})
              (merge base-row {:y (:death_count row) :type "deaths"})])))
        seq)))

(re-frame/reg-sub
 ::transit
 (fn [db _]
   (:transit db)))

(re-frame/reg-sub
 ::transit-fallback
 (fn [db _]
   (:transit-fallback db)))

(defn safe-interval
  "The local clock and the OTP instance clock are not guaranteed to be
  in sync, and in practice the OTP instance provides times ahead of
  local clock. Instead of blowing up, this swallows these errors."
  [a b]
  (try
    (time/interval a b)
    (catch js/Object e
      (time/interval b b))))

(re-frame/reg-sub
 ::transit-stops
 :<- [::transit]
 (fn [transit [_ {:keys [id]}]]
   (let [now  (time/now)]
     (->> transit id :stop-times
          (mapcat :times)
          (map (fn [{time :realtimeDeparture
                     day  :serviceDay
                     live :realtimeState}]
                 (-> time (+ day) (* 1e3)
                     time-coerce/from-long
                     (->> (safe-interval now))
                     time/in-seconds
                     (/ 60)
                     js/Math.ceil)))
          (filter pos?)
          (filter (partial > 90))
          sort
          (take 4)))))

(defn direction->name [direction]
  (condp = direction
    "S" "Downtown"
    "N" "Uptown"))

(re-frame/reg-sub
 ::transit-stops-fallback
 :<- [::transit-fallback]
 (fn [transit-fallback [_ {:keys [direction id]}]]
   (let [{:keys [direction1 direction2]}
         (->> transit-fallback id :stop-times)]
     (->> [direction1 direction2]
          (filter #(= (:name %) (direction->name direction)))
          (mapcat (comp (partial map :minutes) :times))
          (filter pos?)
          sort
          (take 4)))))
