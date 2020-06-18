(ns cockpit.covid-test
  (:require
   [cljs.test :refer-macros [deftest is]]
   [cockpit.covid :as covid]
   [cockpit.events :as events]
   [day8.re-frame.test :as rf-test]
   [re-frame.core :as re-frame]))

(def payload
  [{:date_of_interest   "2020-02-29T00:00:00.000"
    :case_count         "1"
    :hospitalized_count "11"
    :death_count        "0"}
   {:date_of_interest   "2020-03-01T00:00:00.000"
    :case_count         "101"
    :hospitalized_count "111"
    :death_count        "100"}])

(defn stub-http-fetch
  [payload]
  (re-frame/reg-event-fx
    ::covid/fetch-covid
    (fn [_ _]
      {:dispatch [::events/http-success :covid payload]})))

(deftest covid-transformation
  (rf-test/run-test-sync
   (stub-http-fetch payload)

   (re-frame/dispatch [::covid/fetch-covid])

   (let [rows @(re-frame/subscribe [::covid/covid-rows])]
     (is (->> rows
              (filter (comp (partial = "cases") :type))
              first
              :y
              (= "1")))
     (is (->> rows
              (filter (comp (partial = "hospitalized") :type))
              first
              :y
              (= "11")))
     (is (->> rows
              (filter (comp (partial = "deaths") :type))
              first
              :y
              (= "0"))))))
