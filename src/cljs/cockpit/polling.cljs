(ns cockpit.polling
  (:require [cockpit.events :as events]))

(def rules
  [{:interval                 1
    :event                    [::events/timer]
    :dispatch-event-on-start? true}
   {:interval                 900
    :event                    [::events/fetch-stocks "UNH"]
    :dispatch-event-on-start? true}
   {:interval                 900
    :event                    [::events/fetch-stocks "GRPN"]
    :dispatch-event-on-start? true}
   {:interval                 900
    :event                    [::events/fetch-weather]
    :dispatch-event-on-start? true}])
