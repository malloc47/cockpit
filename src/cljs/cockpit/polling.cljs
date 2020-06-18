(ns cockpit.polling
  (:require
   [cockpit.clock :as clock]
   [cockpit.config :as config]
   [cockpit.covid :as covid]
   [cockpit.events :as events]
   [cockpit.stocks :as stocks]
   [cockpit.transit :as transit]
   [cockpit.weather :as weather]
   [clojure.string :as str]))

(def rules
  (vec
   (concat
    [{:interval                 1
      :event                    [::clock/timer]
      :dispatch-event-on-start? true}
     {:interval                 900 ; 15 minutes
      :event                    [::weather/fetch-weather]
      :dispatch-event-on-start? true}
     {:interval                 3600 ; 15 minutes
      :event                    [::covid/fetch-covid]
      :dispatch-event-on-start? true}]
    (map (fn [sym]
           {:interval                 900 ; 15 minutes
            :event                    [::stocks/fetch-stocks sym]
            :dispatch-event-on-start? true})
         config/stocks)
    (map (fn [event]
           {:interval                 30
            :event                    event
            :dispatch-event-on-start? true})
         (concat
          (transit/generate-stop-time-events
           config/transit-stop-whitelist)
          (transit/generate-fallback-stop-time-events
           config/transit-stop-whitelist)))
    (map (fn [event]
           {:interval                 604800 ; 1 week
            :event                    event
            :dispatch-event-on-start? true})
         (transit/generate-stop-events config/transit-stop-whitelist)))))
