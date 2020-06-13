(ns cockpit.weather
  (:require
   [ajax.core :as ajax]
   [cljs-time.coerce :as time-coerce]
   [cljs-time.core :as time]
   [cockpit.config :as config]
   [cockpit.events :as events]
   [cockpit.subs :as subs]
   [day8.re-frame.http-fx]
   [re-frame.core :as re-frame]))

;;; Transliterated from https://github.com/erikflowers/weather-icons/issues/204

(def base-lookup {200 "thunderstorm" 201 "thunderstorm" 202 "thunderstorm" 210 "lightning" 211 "lightning" 212 "lightning" 221 "lightning" 230 "thunderstorm" 231 "thunderstorm" 232 "thunderstorm" 300 "sprinkle" 301 "sprinkle" 302 "rain" 310 "rain-mix" 311 "rain" 312 "rain" 313 "showers" 314 "rain" 321 "sprinkle" 500 "sprinkle" 501 "rain" 502 "rain" 503 "rain" 504 "rain" 511 "rain-mix" 520 "showers" 521 "showers" 522 "showers" 531 "storm-showers" 600 "snow" 601 "snow" 602 "sleet" 611 "rain-mix" 612 "rain-mix" 615 "rain-mix" 616 "rain-mix" 620 "rain-mix" 621 "snow" 622 "snow" 701 "showers" 711 "smoke" 721 "day-haze" 731 "dust" 741 "fog" 761 "dust" 762 "dust" 771 "cloudy-gusts" 781 "tornado" 800 "day-sunny" 801 "cloudy-gusts" 802 "cloudy-gusts" 803 "cloudy-gusts" 804 "cloudy" 900 "tornado" 901 "storm-showers" 902 "hurricane" 903 "snowflake-cold" 904 "hot" 905 "windy" 906 "hail" 957 "strong-wind"})

(def night-lookup { 200 "night-alt-thunderstorm" 201 "night-alt-thunderstorm" 202 "night-alt-thunderstorm" 210 "night-alt-lightning" 211 "night-alt-lightning" 212 "night-alt-lightning" 221 "night-alt-lightning" 230 "night-alt-thunderstorm" 231 "night-alt-thunderstorm" 232 "night-alt-thunderstorm" 300 "night-alt-sprinkle" 301 "night-alt-sprinkle" 302 "night-alt-rain" 310 "night-alt-rain" 311 "night-alt-rain" 312 "night-alt-rain" 313 "night-alt-rain" 314 "night-alt-rain" 321 "night-alt-sprinkle" 500 "night-alt-sprinkle" 501 "night-alt-rain" 502 "night-alt-rain" 503 "night-alt-rain" 504 "night-alt-rain" 511 "night-alt-rain-mix" 520 "night-alt-showers" 521 "night-alt-showers" 522 "night-alt-showers" 531 "night-alt-storm-showers" 600 "night-alt-snow" 601 "night-alt-sleet" 602 "night-alt-snow" 611 "night-alt-rain-mix" 612 "night-alt-rain-mix" 615 "night-alt-rain-mix" 616 "night-alt-rain-mix" 620 "night-alt-rain-mix" 621 "night-alt-snow" 622 "night-alt-snow" 701 "night-alt-showers" 711 "smoke" 721 "day-haze" 731 "dust" 741 "night-fog" 761 "dust" 762 "dust" 781 "tornado" 800 "night-clear" 801 "night-alt-cloudy-gusts" 802 "night-alt-cloudy-gusts" 803 "night-alt-cloudy-gusts" 804 "night-alt-cloudy" 900 "tornado" 902 "hurricane" 903 "snowflake-cold" 904 "hot" 906 "night-alt-hail" 957 "strong-wind"})

(def day-lookup { 200 "day-thunderstorm" 201 "day-thunderstorm" 202 "day-thunderstorm" 210 "day-lightning" 211 "day-lightning" 212 "day-lightning" 221 "day-lightning" 230 "day-thunderstorm" 231 "day-thunderstorm" 232 "day-thunderstorm" 300 "day-sprinkle" 301 "day-sprinkle" 302 "day-rain" 310 "day-rain" 311 "day-rain" 312 "day-rain" 313 "day-rain" 314 "day-rain" 321 "day-sprinkle" 500 "day-sprinkle" 501 "day-rain" 502 "day-rain" 503 "day-rain" 504 "day-rain" 511 "day-rain-mix" 520 "day-showers" 521 "day-showers" 522 "day-showers" 531 "day-storm-showers" 600 "day-snow" 601 "day-sleet" 602 "day-snow" 611 "day-rain-mix" 612 "day-rain-mix" 615 "day-rain-mix" 616 "day-rain-mix" 620 "day-rain-mix" 621 "day-snow" 622 "day-snow" 701 "day-showers" 711 "smoke" 721 "day-haze" 731 "dust" 741 "day-fog" 761 "dust" 762 "dust" 781 "tornado" 800 "day-sunny" 801 "day-cloudy-gusts" 802 "day-cloudy-gusts" 803 "day-cloudy-gusts" 804 "day-sunny-overcast" 900 "tornado" 902 "hurricane" 903 "snowflake-cold" 904 "hot" 906 "day-hail" 957 "strong-wind"})

(defn day?
  [sunrise sunset]
  (< (* sunrise 1000) (.now js/Date) (* sunset 1000)))

(defn request->icon
  [{[{:keys [sunrise sunset]} & _] :daily
    {[{icon-id :id} & _] :weather}       :current}]
  (let [lookup (merge base-lookup (if (day? sunrise sunset)
                                    day-lookup
                                    night-lookup))]
    (get lookup icon-id "day-sunny")))

(defn id->icon
  "Assumes day icon since the sunrise/sunset is not included."
  [icon-id]
  (get (merge base-lookup day-lookup) icon-id "day-sunny"))


(defn number->weekday
  [n]
  (let [lookup {0 "Sun" 1 "Mon" 2 "Tue" 3 "Wed" 4 "Thu" 5 "Fri" 6 "Sat"}]
    (get lookup n "Ukn")))

(defn mm->in
  [mm]
  (* mm 0.0393701))

(defn epoch->local-date
  [epoch]
  (-> epoch
      (* 1000)
      time-coerce/from-long
      time/to-default-time-zone))

(defn open-weather-api-icon
  [code]
  (str "http://openweathermap.org/img/wn/" code "@2x.png"))

;;; Events

(re-frame/reg-event-fx
 ::fetch-weather
 (fn [_ _]
   {:http-xhrio
    {:method :get
     :uri    "http://api.openweathermap.org/data/2.5/onecall"
     :params {:lat   (:lat config/home)
              :lon   (:lon config/home)
              :units "imperial"
              :appid config/open-weather-api-key}
     :response-format (ajax/json-response-format {:keywords? true})
     :on-success      [::events/http-success :weather]
     :on-failure      [::events/http-fail [:weather]]}}))

;;; Subs

(re-frame/reg-sub
 ::weather
 (fn [db _]
   (:weather db)))

(re-frame/reg-sub
 ::sun
 :<- [::weather]
 (fn [{{:keys [sunrise sunset]} :current} _]
   {:sunrise (-> sunrise epoch->local-date .toUsTimeString)
    :sunset  (-> sunset epoch->local-date .toUsTimeString)}))

(re-frame/reg-sub
 ::weather-update-time
 :<- [::subs/clock]
 :<- [::weather]
 (fn [[clock {{:keys [dt]} :current}] _]
   (when dt
     (subs/format-interval
      (subs/safe-interval
       (time-coerce/from-long (* 1000 dt))
       (time-coerce/from-date clock))))))
