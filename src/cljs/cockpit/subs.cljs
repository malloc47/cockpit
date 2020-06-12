(ns cockpit.subs
  (:require
   [cljs-time.coerce :as time-coerce]
   [cljs-time.core :as time]
   [cljs-time.format :as time-format]
   [clojure.string :as str]
   [re-frame.core :as re-frame]))

(defn safe-interval
  "The local clock and the OTP instance clock are not guaranteed to be
  in sync, and in practice the OTP instance provides times ahead of
  local clock. Instead of blowing up, this swallows these errors."
  [a b]
  (try
    (time/interval a b)
    (catch js/Object e
      (time/interval b b))))

(defn format-interval
  [interval]
  (let [base-seconds (time/in-seconds interval)
        seconds      (mod base-seconds 60)
        minutes      (-> base-seconds (/ 60) (mod 60) int)
        hours        (-> base-seconds (/ (* 60 60)) int)]
    (or
     (->> [hours minutes seconds]
          (drop-while zero?)
          reverse
          (map #(str %2 %1) ["s" "m" "h"])
          reverse
          (str/join " ")
          not-empty)
     "0s")))

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
   ;; TODO: a bit annoying to have to dissoc this
   (->> (dissoc stocks :update-time)
        (map (fn [[symbol api-result]]
               [symbol (convert-alpha-vantage-to-sparkline api-result)]))
        (into {}))))

(re-frame/reg-sub
 ::stocks-update-time
 :<- [::clock]
 :<- [::stocks]
 (fn [[clock {:keys [update-time]}] _]
   (format-interval
    (safe-interval
     (time-coerce/from-date update-time)
     (time-coerce/from-date clock)))))

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
