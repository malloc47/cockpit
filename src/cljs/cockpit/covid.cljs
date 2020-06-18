(ns cockpit.covid
  (:require
   [ajax.core :as ajax]
   [cockpit.events :as events]
   [day8.re-frame.http-fx]
   [re-frame.core :as re-frame]))

;;; Events

(re-frame/reg-event-fx
 ::fetch-covid
 (fn [_ _]
   {:http-xhrio
    {:method :get
     :uri    "https://data.cityofnewyork.us/resource/rc75-m7u3.json"
     :response-format (ajax/json-response-format {:keywords? true})
     :on-success      [::events/http-success :covid]
     :on-failure      [::events/http-fail [:covid]]}}))

;;; Subs

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
