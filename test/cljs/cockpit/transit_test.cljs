(ns cockpit.transit-test
  (:require
   [ajax.core :as ajax]
   [cljs.test :refer-macros [deftest testing is]]
   [cockpit.events :as events]
   [cockpit.transit :as transit]
   [cockpit.transit-test-data :as data]
   [clojure.string :as str]
   [day8.re-frame.test :as rf-test]
   [re-frame.core :as re-frame]))

(defn stub-http-fetch
  []
  (re-frame/reg-fx
   :http-xhrio
   (fn [{:keys [uri on-success]}]
     (re-frame/dispatch
      (conj on-success
            (cond
              (str/includes? uri "/stoptimes")
              data/stop-times-payload

              (str/includes? uri "/stops/")
              data/stop-payload

              (str/includes? uri "/routes/")
              data/route-payload))))))

(defn clock-time-relative-to-first-stop-time
  [offset]
  (js/Date.
   ;; The earliest stop time
   (-> (+ 1592539200 84540)
       (* 1e3)
       (+ offset))))

(deftest transit-gtfs
  (rf-test/run-test-sync
   (stub-http-fetch)

   (reset! re-frame.db/app-db
           {:clock
            (clock-time-relative-to-first-stop-time -60000)})

   (re-frame/dispatch [::transit/fetch-stop {:stop-id "MTASBWY:142N"}])
   ;; Should also fetch routes
   (re-frame/dispatch [::transit/fetch-stop-times
                       {:agency-id    "MTASBWY"
                        :stop-id      "MTASBWY:142N"
                        :direction-id "0"}])

   (testing "Raw data is populated"
     (is (= #{"MTASBWY:142N"}
            (->> @(re-frame/subscribe [::transit/stops])
                 vals
                 (map :stop-id)
                 set))
         "Stops were fetched")

     (is (= #{"MTASBWY:1"}
            (->> @(re-frame/subscribe [::transit/routes])
                 vals
                 (map :route-id)
                 set))
         "Routes were fetched when processing stop times")

     (is (= 6 (count @(re-frame/subscribe [::transit/stop-times])))
         "Returns all the stop times for the stop"))

   (testing "Stop times processing"
     (let [stop-times
           (->> @(re-frame/subscribe [::transit/stop-times-processed])
                first
                second)]
       (is (->> stop-times
                (map :minutes)
                (apply max)
                (>= 60 ))
           "View returns stop times in the next hour")

       (is (every? (fn [{:keys [stop route]}]
                     (and (some? stop) (some? route)))
                   stop-times)
           "Stop times are enriched with joined route and stop")))

   (testing "Interval calculation"
     (swap! re-frame.db/app-db
            #(assoc % :clock
                    ;; Roll time back an hour
                    (clock-time-relative-to-first-stop-time (* -60 60 1000))))
     (is (->> @(re-frame/subscribe [::transit/stop-times-processed])
                first
                second
                count
                zero?)
         "No stop times are returned if they are all greater than an hour"))))
