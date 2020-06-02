(ns cockpit.polling
  (:require [cockpit.config :as config]
            [cockpit.events :as events]
            [cockpit.transit :as transit]))

(def rules
  (vec
   (concat
    [{:interval                 1
      :event                    [::events/timer]
      :dispatch-event-on-start? true}
     {:interval                 900
      :event                    [::events/fetch-weather]
      :dispatch-event-on-start? true}
     {:interval                 3600
      :event                    [::events/fetch-covid]
      :dispatch-event-on-start? true}]
    (map (fn [sym]
           {:interval                 900
            :event                    [::events/fetch-stocks sym]
            :dispatch-event-on-start? true})
         config/stocks)
    (->> config/transit-stop-whitelist
         (map (fn [stop]
                {:interval 30
                 :event [::transit/fetch-stop-times stop]
                 :dispatch-event-on-start? true})))
    (->> config/transit-stop-whitelist
         ;; ignore the :id and :direction keys
         (group-by (juxt :agency-id :stop-id))
         vals
         (map (partial
               reduce
               (fn [a b]
                 (let [c (merge a b)]
                   ;; merge :id key into a list of ids
                   (assoc c :id (flatten [(:id a) (:id b)]))))))
         (map (fn [stop]
                {:interval 30
                 :event [::transit/fetch-stop-times-fallback stop]
                 :dispatch-event-on-start? true}))))))
