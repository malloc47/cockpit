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

(def transit-stop-whitelist
  [{:agency-id    "MTASBWY"
    :stop-id      "MTASBWY:625N"
    :direction-id "0"
    :fallback?    true}
   {:agency-id    "MTASBWY"
    :stop-id      "MTASBWY:625S"
    :direction-id "1"
    :fallback?    true}])

(def otp-api-key
  "")

(def otp-uri
  "https://otp-mta-prod.camsys-apps.com/otp")

(def fallback-agency "MTASBWY")

(def fallback-uri
  "http://traintimelb-367443097.us-east-1.elb.amazonaws.com/getTime/")
