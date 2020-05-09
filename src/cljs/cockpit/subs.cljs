(ns cockpit.subs
  (:require
   [re-frame.core :as re-frame]))

(re-frame/reg-sub
 ::time
 (fn [db _]
   (:time db)))
