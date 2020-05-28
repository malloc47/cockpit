(ns cockpit.polling
  (:require [cockpit.config :as config]
            [cockpit.events :as events]))

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
         config/stocks))))
