(ns cockpit.clock-test
  (:require
   [cljs.test :refer-macros [deftest is]]
   [cockpit.clock :as clock]
   [day8.re-frame.test :as rf-test]
   [re-frame.core :as re-frame]
   [re-frame.db :refer [app-db]]))

(deftest timer-event
  (rf-test/run-test-sync
   (re-frame/dispatch [::clock/timer])
   (is (instance? js/Date @(re-frame/subscribe [::clock/clock])))))

(deftest date-time-display
  (reset! re-frame.db/app-db {:clock (js/Date. 1500000000000)})
  (is (= "Thursday, July 13" @(re-frame/subscribe [::clock/day])))
  (is (= "7:40 PM" @(re-frame/subscribe [::clock/time-pt])))
  (is (= "9:40 PM" @(re-frame/subscribe [::clock/time-ct]))))
