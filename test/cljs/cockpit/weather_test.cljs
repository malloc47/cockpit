(ns cockpit.weather-test
  (:require
   [cljs.test :refer-macros [deftest testing is]]
   [cockpit.events :as events]
   [cockpit.weather :as weather]
   [day8.re-frame.test :as rf-test]
   [re-frame.core :as re-frame]))

(def payload
  {:lat 44.65
   :lon -93.69
   :timezone "America/Chicago"
   :timezone_offset -18000
   :current {:sunset 1592445763
             :temp 79.32
             :dt 1592446764
             :sunrise 1592389736
             :humidity 54
             :feels_like 78.55
             :weather [{:id 800 :main "Clear" :description "clear sky" :icon "01n"}]}
   :daily
   [{:sunset 1592445763
     :temp {:day 79.32 :min 71.94 :max 79.32 :night 71.94 :eve 79.32 :morn 79.32}
     :dt 1592416800
     :sunrise 1592389736
     :humidity 54
     :feels_like {:day 74.59 :night 68.94 :eve 74.59 :morn 74.59}
     :weather [{:id 800 :main "Clear" :description "clear sky" :icon "01d"}]}
    {:sunset 1592532181
     :temp {:day 75.9 :min 60.82 :max 79.77 :night 60.82 :eve 72.34 :morn 67.91}
     :dt 1592503200
     :sunrise 1592476142
     :humidity 77
     :feels_like {:day 75.63 :night 59.07 :eve 73.11 :morn 65.44}
     :weather [{:id 502 :main "Rain" :description "heavy intensity rain" :icon "10d"}]
     :rain 18.44}
    {:sunset 1592618598
     :temp {:day 73.36 :min 59.23 :max 75.22 :night 62.19 :eve 72.48 :morn 59.23}
     :dt 1592589600
     :sunrise 1592562550
     :humidity 59
     :feels_like {:day 72.28 :night 62.76 :eve 73.42 :morn 58.3}
     :weather [{:id 804 :main "Clouds" :description "overcast clouds" :icon "04d"}]}
    {:sunset 1592705012
     :temp {:day 75.58 :min 62.24 :max 77.92 :night 64.71 :eve 74.25 :morn 62.24}
     :dt 1592676000
     :sunrise 1592648960
     :humidity 57
     :feels_like {:day 75.9 :night 67.95 :eve 77.41 :morn 61.25}
     :weather [{:id 500 :main "Rain" :description "light rain" :icon "10d"}]
     :rain 2.97}
    {:sunset 1592791424
     :temp {:day 76.95 :min 63.12 :max 78.4 :night 65.32 :eve 76.19 :morn 63.12}
     :dt 1592762400
     :sunrise 1592735372
     :humidity 64
     :feels_like {:day 76.91 :night 65.07 :eve 79.77 :morn 64.83}
     :weather [{:id 501 :main "Rain" :description "moderate rain" :icon "10d"}]
     :rain 7.02}
    {:sunset 1592877834
     :temp {:day 72.36 :min 56.57 :max 72.36 :night 56.57 :eve 69.6 :morn 64.44}
     :dt 1592848800
     :sunrise 1592821787
     :humidity 75
     :feels_like {:day 69.67 :night 52.07 :eve 66.88 :morn 64.96}
     :weather [{:id 500 :main "Rain" :description "light rain" :icon "10d"}]
     :rain 1.4}
    {:sunset 1592964242
     :temp {:day 69.06 :min 54.66 :max 69.06 :night 54.66 :eve 64.99 :morn 55.8}
     :dt 1592935200
     :sunrise 1592908203
     :humidity 62
     :feels_like {:day 61.99 :night 50.31 :eve 60.91 :morn 52.54}
     :weather [{:id 501 :main "Rain" :description "moderate rain" :icon "10d"}]
     :rain 3.23}
    {:sunset 1593050647
     :temp {:day 56.95 :min 51.89 :max 61.95 :night 53.38 :eve 61.14 :morn 51.89}
     :dt 1593021600
     :sunrise 1592994622
     :humidity 84
     :feels_like {:day 52.21 :night 49.57 :eve 55.42 :morn 44.6}
     :weather [{:id 500 :main "Rain" :description "light rain" :icon "10d"}]
     :rain 1.39}]})

(defn stub-http-fetch
  [payload]
  (re-frame/reg-event-fx
   ::weather/fetch-weather
   (fn [_ _]
     {:dispatch [::events/http-success :weather payload]})))

(deftest weather-display
  (rf-test/run-test-sync
   (stub-http-fetch payload)

   (re-frame/dispatch [::weather/fetch-weather])

   (testing "sunset and sunrise"
     (let [{:keys [sunrise sunset]} @(re-frame/subscribe [::weather/sun])]
       ;; TODO: The lat/lon in the above payload is on CST but since
       ;; these tests have been running on EST these times are 1 hour
       ;; later than they are in the local time
       (is (= "6:28 AM" sunrise))
       (is (= "10:02 PM" sunset))))

   (testing "current conditions"
     (let [{:keys [humidity feels-like description rain snow temp low high]}
           @(re-frame/subscribe [::weather/conditions])]
       (is (= humidity 54))
       (is (= feels-like "78°"))
       (is (= description "Clear sky"))
       (is (nil? rain))
       (is (nil? snow))
       (is (= temp "79°"))
       (is (= low "71°"))
       (is (= high "79°"))))

   (testing "icon"
     (is (#{"day-sunny" "night-clear"}
          @(re-frame/subscribe [::weather/icon]))))

   (testing "forecast"
     (let [forecast @(re-frame/subscribe [::weather/forecast])]
       (is (= 6 (count forecast)))
       (is (> (-> forecast first :epoch) (-> payload :current :dt))
           "Today should not be included in the forecast")))))
