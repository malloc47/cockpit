(ns cockpit.events
  (:require
   [re-frame.core :as re-frame]
   [cockpit.db :as db]
   [day8.re-frame.tracing :refer-macros [fn-traced]]))

(defn dispatch-timer-event
  []
  (let [now (js/Date.)]
    (re-frame/dispatch [::timer now])))

(defonce do-timer (js/setInterval dispatch-timer-event 1000))

(re-frame/reg-event-db
 ::initialize-db
 (fn-traced [_ _]
            db/default-db))

(re-frame/reg-event-db
 ::timer
 (fn [db [_ new-time]]
   (assoc db :time new-time)))
