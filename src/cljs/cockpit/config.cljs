(ns cockpit.config)

(def debug?
  ^boolean goog.DEBUG)

(def open-weather-api-key
  "")

(def alpha-vantage-api-key
  "")

(def lat 1)
(def lon 1)

(def stocks ["AAPL" "IBM"])
(def transit-stop-ids
  [])

(def id-prefix "MTASBWY:")

(def transit-stop-whitelist
  [{:id :6N
    :agency-id "MTASBWY"
    :stop-id   "625"
    :direction "N"}
   {:id :6S
    :agency-id "MTASBWY"
    :stop-id   "625"
    :direction "S"}])

(def transit-stops
  {:6S {:stop-id  "MTASBWY:625S"
        :route-id "MTASBWY:6"
        :img      "https://new.mta.info/themes/custom/bootstrap_mta/images/icons/6.svg"
        :fallback? true}
   :6N {:stop-id  "MTASBWY:625N"
        :route-id "MTASBWY:6"
        :img      "https://new.mta.info/themes/custom/bootstrap_mta/images/icons/6.svg"
        :fallback? true}})

(def otp-api-key
  "")
