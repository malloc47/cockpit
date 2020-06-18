(ns cockpit.clock
  (:require
   [re-frame.core :as re-frame]))

;;; Events

(re-frame/reg-event-db
 ::timer
 (fn [db [_ new-time]]
   (assoc db :clock (js/Date.))))

;;; Subs

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
