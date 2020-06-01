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

(def otp-api-key
  "")
