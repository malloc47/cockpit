(ns cockpit.config)

(def debug?
  ^boolean goog.DEBUG)

(def open-weather-api-key
  "")

(def iex-api-key
  "")

(def home
  {:lat 1
   :lon 1})

(def work
  {:lat 1
   :lon 1})

(def stocks ["AAPL" "IBM"])

(def transit-stop-whitelist
  [{:agency-id    "MTASBWY"
    :stop-id      "MTASBWY:625N"
    :direction-id "0"}
   {:agency-id    "MTASBWY"
    :stop-id      "MTASBWY:625S"
    :direction-id "1"}])

(def otp-api-key
  "")

(def otp-uri
  "https://otp-mta-prod.camsys-apps.com/otp")
