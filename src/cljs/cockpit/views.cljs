(ns cockpit.views
  (:require
   [re-frame.core :as re-frame]
   [clojure.string :as str]
   [cockpit.config :as config]
   [cockpit.subs :as subs]
   [cockpit.weather :as weather :refer [mm->in]]
   [cockpit.transit :as transit]
   [goog.string :as gstring]
   ["@material-ui/core/Avatar" :default Avatar]
   ["@material-ui/core/Card" :default Card]
   ["@material-ui/core/CardContent" :default CardContent]
   ["@material-ui/core/Container" :default Container]
   ["@material-ui/core/Grid" :default Grid]
   ["@material-ui/core/CssBaseline" :default CssBaseline]
   ["@material-ui/core/Typography" :default Typography]
   ["react-sparklines" :refer [Sparklines SparklinesLine
                               SparklinesReferenceLine dataProcessing]]
   ["@material-ui/core/colors/green" :default green]
   ["@material-ui/core/colors/lightBlue" :default lightBlue]
   ["react-vega" :refer [VegaLite]]))

(def color-scheme (js->clj green))
(def accent-scheme (js->clj lightBlue))

(defn clock []
  [:> Card {:style {:height "100%"}}
   [:> CardContent
    [:> Typography {:align "center" :variant "h1"
                    :style {:white-space "nowrap"}}
     @(re-frame/subscribe [::subs/time])]
    [:> Typography {:align "center" :variant "h4"
                    :style {:white-space "nowrap"}}
     @(re-frame/subscribe [::subs/day])]
    [:> Grid {:container true :spacing 0 :direction "row"
              :justify "center" :alignItems "center"}
     [:> Grid {:item true :xs 6}
      [:> Typography {:align "center" :variant "h6"
                      :style {:margin-top "0.5em"}}
       @(re-frame/subscribe [::subs/time-pt])]
      [:> Typography {:align "center" :variant "body2" :color "textSecondary"
                      :style {:margin-top "0.5em"}}
       "San Francisco"]]
     [:> Grid {:item true :xs 6}
      [:> Typography {:align "center" :variant "h6"
                      :style {:margin-top "0.5em"}}
       @(re-frame/subscribe [::subs/time-ct])]
      [:> Typography {:align "center" :variant "body2" :color "textSecondary"
                      :style {:margin-top "0.5em"}}
       "Chicago"]]]
    (let [{:keys [sunrise sunset]} @(re-frame/subscribe [::weather/sun])]
      [:> Typography {:align "center"
                      :variant "h6"
                      :style {:margin-top "0.5em"}}
       [:i {:class (str "wi wi-sunrise") :style {:color (get accent-scheme "600")}}]
       sunrise
       (gstring/unescapeEntities "&#8194;")
       [:i {:class (str "wi wi-sunset") :style {:color (get accent-scheme "600")}}]
       sunset])]])

(def sparkline-height 30)

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
    [:<>
     [:> Typography {:variant "body1"
                     :display "inline"
                     :color "textSecondary"}
      (name symbol) " "]
     [:> Typography {:variant "h6"
                     :display "inline"}
      "$" (last data)]
     [:> Typography {:variant "body1"
                     :display "inline"
                     :style {:color color}}
      (gstring/unescapeEntities "&nbsp;")
      diff-display " (" percent "%) " (if up? "▲" "▼")]
     [:> Sparklines {:data (or data []) :height sparkline-height :margin 0}
      [:> SparklinesLine {:color color}]
      [:> SparklinesReferenceLine
       {:type "custom" :value (correct-reference-line data)
        :style {:stroke "black"
                :strokeOpacity 0.75
                :strokeDasharray "1, 3" }}]]]))

(defn stocks
  []
  [:> Card  {:style {:height "100%"}}
   [:> CardContent
    (into
     [:<>]
     (vec (map (fn [sym] [stock-chart (keyword sym)]) config/stocks)))
    [:div {:style {:float "right"}}
      [:> Typography {:variant "body2" :color "textSecondary"}
       @(re-frame/subscribe [::subs/stocks-update-time])]]]])

(defn weather []
  (let [weather @(re-frame/subscribe [::weather/weather])]
    [:<>
     [:> Grid {:container true :spacing 0 :direction "row"
               :justify "center" :alignItems "center"}
      [:> Grid {:item true :xs 3}
       [:> Typography {:variant "h1"}
        [:i {:class (str "wi wi-" (weather/request->icon weather))
             :style {:color (get color-scheme "400")}}]]]
      [:> Grid {:item true :xs 5 :style {:text-align "center"}}
       [:> Typography {:align "center" :variant "h1"
                       :display "inline"
                       :style {:margin-top "0.1em"}}
        (-> weather :current :temp int) "°"]]
      [:> Grid {:item true :xs 2}
       (let [{low :min high :max} (-> weather :daily first :temp)]
         [:> Typography {:align "right" :display "inline"
                         :variant "h4" :style {:float "right"}}
          (int high) "°"
          [:br]
          (int low) "°"])]]

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
            (apply concat [:> Typography {:align "center" :color "textSecondary"}])
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
                          :variant "body1"
                          :align "center"
                          :style {:margin-bottom "0.5em"}}
           (-> date weather/epoch->local-date .getWeekday weather/number->weekday)]
          [:> Typography {:align "center" :variant "h5"}
           [:i {:class (str "wi wi-" (weather/id->icon icon-id))
                :style {:color (get accent-scheme "600")}}]]
          [:> Typography {:align "center" :variant "subtitle2"}
           (int high) "°"
           (gstring/unescapeEntities "&#8194;")
           [:span {:style {:color "rgb(132, 132, 132)"}} (int low) "°"]
           [:span {:style {:color (get accent-scheme "600")}}
            (when rain
              [:<>
               [:br]
               (list " " (some-> rain mm->in (round 1)) "\"")])]
           [:span {:style {:color "red"}}
            (when snow
              [:<>
               (list " " (some-> snow mm->in (round 1)) "\"")])]]])
       (->> weather :daily rest (take 6)))]
     [:div {:style {:float "right"}}
      [:> Typography {:variant "body2" :color "textSecondary"}
       @(re-frame/subscribe [::weather/weather-update-time])]]]))

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

(def direction-id->arrow
  {"0" "▲"
   "1" "▼"
   nil ""})

(defn transit []
  [:> Card  {:style {:height "100%"}}
   [:> CardContent
    [:> Grid {:container true :spacing 1 :alignItems "center"}
     (map
      (fn [[{{:keys [color text-color short-name route-id]} :route
             {:keys [direction-id stop-id]} :stop}
            stop-times]]
        [:<> {:key (str stop-id "-" route-id)}

         [:> Grid {:item true :xs 1}
          [:> Typography {:variant "h4" :color "textSecondary"}
           ;; TODO handle nil as no direction
           (if (->> config/transit-stop-whitelist
                    (filter (comp (partial = stop-id) :stop-id))
                    first
                    :swap-direction?)
             (get direction-id->arrow (get {"0" "1" "1" "0"} direction-id) "")
             (get direction-id->arrow direction-id ""))]]

         [:> Grid {:item true :xs 2}
          ;; TODO resize to vw (see previous commit)
          [:> Avatar {:style {:background-color (str "#" color)
                              :color (str "#" text-color)
                              :font-weight "bold"}}
           short-name]]

         (->> (concat stop-times (repeat nil))
              (map-indexed
               (fn [idx {:keys [minutes] :as stop-time}]
                 [:<> {:key idx}
                  [:> Grid {:item true :xs 2}
                   (when stop-time
                     [:> Typography
                      {:variant "h5" :style {:font-size "1.9rem"}}
                      [:span {:style {:color (if (> minutes 0) "black" "green")}}
                       (if (> minutes 0)
                         [:<> minutes
                          [:span
                           {:style
                            {:color "rgba(0,0,0,0.54)"}} "m "]]
                         "Now ")]])]]))
              (take 4))

         [:> Grid {:item true :xs 1}]])

      @(re-frame/subscribe [::transit/stop-times-processed]))]

    [:div {:style {:float "right"}}
     [:> Typography {:variant "body2" :color "textSecondary"}
      @(re-frame/subscribe [::transit/stop-times-update-interval])]]]])

(defn main-panel []
  (let [card-opts {:item true :xs 12 :sm 12 :md 6  :lg 4}]
    [:> CssBaseline
     [:> Container {:maxWidth false}
      [:> Grid {:container true :spacing 1
                :style {:padding-top "5px"}}

       [:> Grid card-opts
        [:> Card  {:style {:height "100%"}}
         [:> CardContent [weather]]]]

       [:> Grid card-opts [clock]]

       [:> Grid card-opts [transit]]

       [:> Grid card-opts [stocks]]

       [:> Grid card-opts [covid]]]]]))
