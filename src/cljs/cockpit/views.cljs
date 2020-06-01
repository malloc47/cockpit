(ns cockpit.views
  (:require
   [re-frame.core :as re-frame]
   [clojure.string :as str]
   [cockpit.config :as config]
   [cockpit.subs :as subs]
   [cockpit.weather :as weather :refer [mm->in]]
   [goog.string :as gstring]
   ["@material-ui/core" :refer [Avatar Button Card CardActionArea CardActions
                                CardContent CardHeader CardMedia Container Grid
                                CssBaseline Paper Typography ThemeProvider]]
   ["react-sparklines" :refer [Sparklines SparklinesLine
                               SparklinesReferenceLine dataProcessing]]
   ["@material-ui/core/styles" :refer [makeStyles]]
   ["@material-ui/core/colors/green" :default green]
   ["@material-ui/core/colors/lightBlue" :default lightBlue]
   ["react-vega" :refer [VegaLite]]))

(def color-scheme (js->clj green))
(def accent-scheme (js->clj lightBlue))

(defn clock []
  [:> Card {:style {:height "100%"}}
   [:> CardContent
    [:> Typography {:align "center" :variant "h1" :style {:font-size "6vw"}}
     @(re-frame/subscribe [::subs/time])]
    [:> Typography {:align "center" :variant "h2" :style {:font-size "3vw"}}
     @(re-frame/subscribe [::subs/day])]
    [:> Grid {:container true :spacing 0 :direction "row"
              :justify "center" :alignItems "center"}
     [:> Grid {:item true :xs 6}
      [:> Typography {:align "center" :variant "h3"
                      :style {:font-size "1.5vw" :margin-top "0.5em"}}
       @(re-frame/subscribe [::subs/time-pt])]
      [:> Typography {:align "center" :variant "h3" :color "textSecondary"
                      :style {:font-size "1vw" :margin-top "0.5em"}}
       "San Francisco"]]
     [:> Grid {:item true :xs 6}
      [:> Typography {:align "center" :variant "h3"
                      :style {:font-size "1.5vw" :margin-top "0.5em"}}
       @(re-frame/subscribe [::subs/time-ct])]
      [:> Typography {:align "center" :variant "h3" :color "textSecondary"
                      :style {:font-size "1vw" :margin-top "0.5em"}}
       "Chicago"]]]
    (let [{:keys [sunrise sunset]} @(re-frame/subscribe [::subs/sun])]
      [:> Typography {:align "center"
                      :variant "h3"
                      :style {:font-size "1.5vw" :margin-top "0.5em"}}
       [:i {:class (str "wi wi-sunrise") :style {:color (get accent-scheme "600")}}]
       sunrise
       (gstring/unescapeEntities "&#8194;")
       [:i {:class (str "wi wi-sunset") :style {:color (get accent-scheme "600")}}]
       sunset])]])

(defn cute []
  [:> Card  {:style {:height "100%"}}
   [:> CardActionArea
    [:> CardMedia
     {:image "https://icatcare.org/app/uploads/2018/07/Thinking-of-getting-a-cat.png"
      :style {:height "300px"}}]]
   [:> CardContent "A Cat."]])

(def sparkline-height 50)

(defn correct-reference-line
  "The 'custom' option for SparklinesReferenceLine is not wired through
  the same internal translation to SVG units, and instead is printed
  literally on the SVG as the value given. Until this is patched, this
  recreates the expected translation, assuming the first datapoint is
  the reference line and is included in the scaling."
  [data]
  (let [top    (apply max data)
        bottom (apply min data)
        ref    (first data)]
    (- sparkline-height (* (/ (- ref bottom) (- top bottom)) sparkline-height))))

(defn round
  [num & [decimals]]
  (let [base (->> (repeat 10) (take (or decimals 2)) (reduce *))]
    (/ (.round js/Math (* base num)) base)))

(defn stock-chart [symbol]
  (let [data         (clj->js (symbol @(re-frame/subscribe [::subs/stocks-sparkline])))
        diff         (round (- (last data) (first data)))
        up?          (>= (last data) (first data))
        diff-display (str (if (>= diff 0) "+" "") diff)
        percent      (round (* (- 1 (/ (first data) (last data))) 100))
        color        (if up? "green" "red")]
    [:div
     [:> Typography {:variant "body1"
                     :display "inline"
                     :color "textSecondary"}
      (name symbol) " "]
     [:> Typography {:variant "h6"
                     :display "inline"}
      (last data)]
     [:> Typography {:variant "body1"
                     :display "inline"
                     :color "textSecondary"}
      " USD "]
     [:> Typography {:variant "body1"
                     :display "inline"
                     :style {:color color}}
      diff-display " (" percent "%) " (if up? "▲" "▼")]
     [:> Sparklines {:data (or data []) :height sparkline-height :margin 0}
      [:> SparklinesLine {:color color}]
      [:> SparklinesReferenceLine
       {:type "custom" :value (correct-reference-line data)
        :style {:stroke "black"
                :strokeOpacity 0.75
                :strokeDasharray "1, 3" }}]]]))

(defn weather [symbol]
  (let [weather @(re-frame/subscribe [::subs/weather])]
    [:div
     [:> Typography {:variant "h5"
                     :color "textSecondary"
                     :style {:font-size "1.5vw"
                             :margin-bottom "0.5em"}}
      "New York, NY"]

     [:> Grid {:container true :spacing 0 :direction "row"
               :justify "center" :alignItems "center"}
      [:> Grid {:item true :xs 3}
       [:i {:class (str "wi wi-" (weather/request->icon weather))
            :style {:color (get color-scheme "400")
                    :font-size "5vw"}}]
       #_[:img {:src (weather/open-weather-api-icon
                    (-> weather :current :weather first :icon))}]]
      [:> Grid {:item true :xs 4 :style {:text-align "center"}}
       [:> Typography {:align "center" :variant "h1"
                       :display "inline"
                       :style {:font-size "5vw" :margin-top "0.1em"}}
        (-> weather :current :temp int) "°"]]
      [:> Grid {:item true :xs 2}
       (let [{low :min high :max} (-> weather :daily first :temp)]
         [:> Typography {:align "left" :display "inline"
                         :variant "h2" :style {:font-size "2vw"}}
          (int high) "°"
          [:br]
          (int low) "°"])]

      #_[:> Grid {:item true :xs 3}
       (let [{:keys [morn day eve night]} (-> weather :daily first :temp)]
         [:> Typography {:align "left" :display "inline"
                         :variant "h2" :style {:font-size "1vw"}}
          "Mor: " (int morn) "°"
          [:br]
          "Day: " (int day) "°"
          [:br]
          "Eve: " (int eve) "°"
          [:br]
          "Ngt: " (int night) "°"])]]

     (let [humidity    (-> weather :current :humidity)
           feels-like  (some-> weather :current :feels_like int)
           description (some-> weather :current :weather first
                               :description str/capitalize)
           rain        (some-> weather :daily first :rain mm->in (round 2))
           snow        (some-> weather :daily first :snow mm->in (round 1))]
       (->> [{:content description :render? description}
             {:prefix "Feels like "
              :content [:span {:style {:color (get accent-scheme "600")}}
                        feels-like "°"]
              :render? true}
             {:content [:span {:style {:color (get accent-scheme "600")}}
                        humidity [:i {:class "wi wi-humidity"}]]
              :render? true}
             {:postfix " rain"
              :content [:span {:style {:color (get accent-scheme "600")}} rain "\""]
              :render? rain}
             {:postfix " snow"
              :content [:span {:style {:color "red"}} snow "\""]
              :render? snow}]
            (map (fn [{:keys [prefix postfix content render?]}]
                   (if render?
                     (->> [prefix content postfix] (remove nil?) vec)
                     [])))
            (remove empty?)
            (interpose [" | "])
            (apply concat [:> Typography {:align "center" :color "textSecondary"
                                          :style {:font-size "1.1vw"}}])
            vec))

     [:> Grid {:container true :spacing 1 :direction "row"
               :justify "center" :alignItems "flex-start"
               :style {:margin-top "1em"}}
      (map
       (fn [{date                 :dt
             {low :min high :max} :temp
             rain                 :rain
             snow                 :snow
             [{icon-id :id} & _]  :weather}]
         ^{:key date}
         [:> Grid {:item true :xs 2}
          [:> Typography {:key date
                          :align "center"
                          :style {:font-size "1vw"
                                  :margin-bottom "0.5em"}}
           (-> date subs/epoch->local-date .getWeekday weather/number->weekday)]
          [:> Typography {:align "center"}
           [:i {:class (str "wi wi-" (weather/id->icon icon-id))
                :style {:font-size "2vw"
                        :color (get accent-scheme "600")}}]]
          [:> Typography {:align "center" :style {:font-size "1vw"}}
           (int high) "°"
           (gstring/unescapeEntities "&#8194;")
           [:span {:style {:color "rgb(132, 132, 132)"}} (int low) "°"]
           [:span {:style {:color (get accent-scheme "600")}}
            (when rain
              (list " " (some-> rain mm->in (round 1)) "\""))]
           [:span {:style {:color "red"}}
            (when snow
              (list " " (some-> snow mm->in (round 1)) "\""))]]])
       (->> weather :daily rest (take 6)))]]))

(defn covid []
  [:> Card  {:style {:height "100%"}}
   [:> CardContent {:style {:height "250px"}}
    (when-let [data @(re-frame/subscribe [::subs/covid-rows])]
      [:<>
       [:> VegaLite {:actions false
                     :spec {:width    "container"
                            :height   130
                            :mark     {:type "line"}
                            :encoding
                            {:x {:field "date"
                                 :type "temporal"
                                 :axis {:title nil
                                        :grid  false}}
                             :y {:field "y"
                                 :type "quantitative"
                                 :axis {:title nil
                                        :grid  false}
                                 :range [0,nil]
                                 :scale {:type "sqrt"}}
                             :color {:field "type" :type "nominal"
                                     :legend
                                     {:orient "top"
                                      :title nil
                                      :symbolType "circle"}}}
                            :autosize {:resize true}
                            :data
                            {:name "table"
                             :format
                             {:parse
                              {:date_of_interest "date:'%Y-%m-%dT%H:%M:%S.%L'"}}}}
                     :data {:table data}
                     :style {:width "100%"}}]
       [:> Typography {:variant "h5"
                       :color "textSecondary"}
        "Total Cases: " (->> data
                             (filter #(= (:type %) "cases"))
                             last
                             :y)]])]])

(defn transit []
  [:> Card  {:style {:height "100%"}}
   [:> CardContent
    [:> Grid {:container true :spacing 1}

     [:> Grid {:item true :xs 1}
      [:> Typography {:variant "h4" :color "textSecondary"} "▼"]]
     [:> Grid {:item true :xs 2}
      [:> Avatar {:style {:background-color "#FCCC0A" :color "#000000"
                          :font-weight "bold"}}
       "Q"]]
     [:> Grid {:item true :xs 9}
      [:> Typography {:variant "h4"}
       (->> @(re-frame/subscribe [::subs/transit-stops-fallback
                                  (nth config/transit-stop-whitelist 0)])
            (map (fn [minutes] (str minutes "m")))
            (str/join ", "))]]

     [:> Grid {:item true :xs 1}
      [:> Typography {:variant "h4"  :color "textSecondary"} "▲"]]
     [:> Grid {:item true :xs 2}
      [:> Avatar {:style {:background-color "#00933C" :color "#FFFFFF"
                          :font-weight "bold"}}
       "6"]]
     [:> Grid {:item true :xs 9}
      [:> Typography {:variant "h4"}
       (->> @(re-frame/subscribe [::subs/transit-stops-fallback
                                  (nth config/transit-stop-whitelist 1)])
            (map (fn [minutes] (str minutes "m")))
            (str/join ", "))]]

     [:> Grid {:item true :xs 1}
      [:> Typography {:variant "h4"  :color "textSecondary"} "▲"]]
     [:> Grid {:item true :xs 2}
      [:> Avatar {:style {:background-color "#00933C" :color "#FFFFFF"
                          :font-weight "bold"}}
       "6"]]
     [:> Grid {:item true :xs 9}
      [:> Typography {:variant "h4"}
       (->> @(re-frame/subscribe [::subs/transit-stops
                                  (nth config/transit-stop-whitelist 1)])
            (map (fn [minutes] (str minutes "m")))
            (str/join ", "))]]

     [:> Grid {:item true :xs 1}
      [:> Typography {:variant "h4"  :color "textSecondary"} "▼"]]
     [:> Grid {:item true :xs 2}
      [:> Avatar {:style {:background-color "#00933C" :color "#FFFFFF"
                          :font-weight "bold"}}
       "6"]]
     [:> Grid {:item true :xs 9}
      [:> Typography {:variant "h4"}
       (->> @(re-frame/subscribe [::subs/transit-stops-fallback
                                  (nth config/transit-stop-whitelist 2)])
            (map (fn [minutes] (str minutes "m")))
            (str/join ", "))]]

     [:> Grid {:item true :xs 1}
      [:> Typography {:variant "h4"  :color "textSecondary"} "▼"]]
     [:> Grid {:item true :xs 2}
      [:> Avatar {:style {:background-color "#00933C" :color "#FFFFFF"
                          :font-weight "bold"}}
       "6"]]
     [:> Grid {:item true :xs 9}
      [:> Typography {:variant "h4"}
       (->> @(re-frame/subscribe [::subs/transit-stops
                                  (nth config/transit-stop-whitelist 2)])
            (map (fn [minutes] (str minutes "m")))
            (str/join ", "))]]

     ]]])

(defn main-panel []
  (let [card-opts {:item true :xs 4 :sm 4 :md 4  :lg 4}]
    [:> CssBaseline
     [:> Container {:maxWidth false}
      [:> Grid {:container true :spacing 1}

       [:> Grid card-opts
        [:> Card  {:style {:height "100%"}}
         [:> CardContent [weather]]]]

       [:> Grid card-opts [clock]]

       [:> Grid card-opts
        [:> Card  {:style {:height "100%"}}
         [:> CardContent
          (into
           [:<>]
           (vec (map (fn [sym] [stock-chart (keyword sym)]) config/stocks)))]]]

       [:> Grid card-opts [covid]]

       [:> Grid card-opts [transit]]

       #_[:> Grid card-opts [cute]]]]]))
