(ns cockpit.stocks-test
  (:require
   [cljs.test :refer-macros [deftest testing is]]
   [clojure.string :as str]
   [cockpit.stocks :as stocks]
   [day8.re-frame.test :as rf-test]
   [re-frame.core :as re-frame]))

(def intraday-payload
  [{:date "2020-10-10"
    :minute "10:10"
    :close 10.0
    :numberOfTrades 50}
   {:date "2020-10-10"
    :minute "10:11"
    :close 10.1
    :numberOfTrades 20}
   {:date "2020-10-10"
    :minute "10:12"
    :close nil
    :numberOfTrades 0}
   {:date "2020-10-10"
    :minute "10:13"
    :close 12.5
    :numberOfTrades 4}
   {:date "2020-10-10"
    :minute "10:14"
    :close 11.8
    :numberOfTrades 7}
   {:date "2020-10-10"
    :minute "10:15"
    :close 11.2
    :numberOfTrades 2}])

(def previous-payload
  {:date  "2020-10-09"
   :close 8.0})

(defn stub-http-fetch
  []
  (re-frame/reg-fx
   :http-xhrio
   (fn [{:keys [uri on-success]}]
     (re-frame/dispatch
      (conj on-success
            (cond
              (str/includes? uri "intraday-prices")
              intraday-payload

              (str/includes? uri "/previous")
              previous-payload))))))

(deftest stock-fetch
  (rf-test/run-test-sync
   (stub-http-fetch)

   (re-frame/dispatch [::stocks/fetch-stocks "MSFT"])

   (testing "Raw data is populated"
     (is (-> @(re-frame/subscribe [::stocks/stocks])
             keys
             set
             (contains? :MSFT)))

     (is (-> @(re-frame/subscribe [::stocks/stocks])
             :MSFT
             keys
             set
             (= #{:data :previous-close}))))

   (testing "Sparkline transformation"
     (is (= [8.0 10.0 10.1 12.5 11.8 11.2]
            (:MSFT @(re-frame/subscribe [::stocks/stocks-sparkline])))))

   (testing "Previous day is cached"
     (let [counter (atom 0)]
       (re-frame/reg-event-db
        ::stocks/persist-prev-close
        (fn [db _]
          (swap! counter inc)
          db))

       (re-frame/dispatch [::stocks/fetch-stocks "MSFT"])

       (is (zero? @counter)
           "Did not re-fetch previous day prices")

       (re-frame/dispatch [::stocks/fetch-stocks "AAPL"])

       (is (pos? @counter)
           "Fetched previous day prices")))))
