(ns cockpit.polling
  (:require [cockpit.config :as config]
            [cockpit.events :as events]
            [cockpit.transit :as transit]
            [clojure.string :as str]))

(def rules
  (vec
   (concat
    [{:interval                 1
      :event                    [::events/timer]
      :dispatch-event-on-start? true}
     {:interval                 900 ; 15 minutes
      :event                    [::events/fetch-weather]
      :dispatch-event-on-start? true}
     {:interval                 3600 ; 15 minutes
      :event                    [::events/fetch-covid]
      :dispatch-event-on-start? true}]
    (map (fn [sym]
           {:interval                 900 ; 15 minutes
            :event                    [::events/fetch-stocks sym]
            :dispatch-event-on-start? true})
         config/stocks)
    (->> config/transit-stop-whitelist
         (filter (comp not :fallback?))
         (map (fn [stop]
                {:interval 30
                 :event [::transit/fetch-stop-times stop]
                 :dispatch-event-on-start? true})))
    (->> config/transit-stop-whitelist
         (filter :fallback?)
         ;; create a key without the direction for grouping
         (map #(assoc % :stop (->> %
                                   :stop-id
                                   drop-last
                                   (str/join ""))
                      :stop-id (list (:stop-id %))))
         ;; ignore the other keys
         (group-by (juxt :agency-id :stop))
         vals
         (map (partial
               reduce
               (fn [a b]
                 (let [c (merge a b)]
                   (-> c
                       ;; merge :id key into a list of ids
                       (assoc :stop-id (concat (:stop-id a) (:stop-id b)))
                       (dissoc :stop))))))
         (map (fn [stop]
                {:interval 30
                 :event [::transit/fetch-stop-times-fallback stop]
                 :dispatch-event-on-start? true})))
    (->> config/transit-stop-whitelist
         (map (fn [stop]
                {:interval 604800 ; 1 week
                 :event    [::transit/fetch-stop stop]
                 :dispatch-event-on-start? true}))))))
