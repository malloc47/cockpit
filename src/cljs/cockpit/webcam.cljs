(ns cockpit.webcam
  (:require
   [cockpit.config :as config]
   [re-frame.core :as re-frame]))

;;; Events

(re-frame/reg-event-db
 ::update-image
 (fn [db _]
   (assoc-in db [:webcam :url] (str config/webcam-link "?math=" (rand)))))

;;; Subs

(re-frame/reg-sub
 ::url
 (fn [db _]
   (get-in db [:webcam :url])))
