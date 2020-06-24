(ns cockpit.views
  (:require
   [cockpit.clock :as clock]
   [cockpit.config :as config]
   [cockpit.covid :as covid]
   [cockpit.events :as events]
   [cockpit.stocks :as stocks]
   [cockpit.transit :as transit]
   [cockpit.utils :refer [round]]
   [cockpit.weather :as weather]
   [goog.string :as gstring]
   [re-frame.core :as re-frame]
   [reagent.core :refer [as-element]]
   ["jss" :default jss]
   ["jss-plugin-nested" :default jssNested]
   ["@material-ui/core/Avatar" :default Avatar]
   ["@material-ui/core/Card" :default Card]
   ["@material-ui/core/CardActions" :default CardActions]
   ["@material-ui/core/CardContent" :default CardContent]
   ["@material-ui/core/Container" :default Container]
   ["@material-ui/core/Grid" :default Grid]
   ["@material-ui/core/CssBaseline" :default CssBaseline]
   ["@material-ui/core/IconButton" :default IconButton]
   ["@material-ui/icons/Refresh" :default RefreshIcon]
   ["@material-ui/core/Typography" :default Typography]
   ["@material-ui/core/styles" :refer [styled]]
   ["@material-ui/core/colors/green" :default green]
   ["@material-ui/core/colors/lightBlue" :default lightBlue]
   ["react-sparklines" :refer [Sparklines SparklinesLine
                               SparklinesReferenceLine]]
   ["react-vega" :refer [VegaLite]]))

(.use jss (jssNested))

(def color-scheme (js->clj green))
(def accent-scheme (js->clj lightBlue))

(def styles
  {:text-secondary  {:color "rgb(132, 132, 132)"}
   :reference-line  {:stroke          "black"
                     :strokeOpacity   0.75
                     :strokeDasharray "1, 3" }
   :highlight-color {:color (get color-scheme "400")}
   :accent-color    {:color (get accent-scheme "600")}
   :alert-color     {:color "red"}
   :right-aligned   {:width             "100%"
                     "& > :first-child" {:float "right"}}})

(def classes
  (-> styles
      clj->js
      (->> (.createStyleSheet jss))
      .attach
      (aget "classes")
      (js->clj :keywordize-keys true)))

(defn style
  [component css]
  ((styled component) (clj->js css)))

(def ^:const FixedHeightCard
  ;; Customize to get more than 2 cards on a screen
  (style Card {:height "48vh"}))

(def ^:const CardContentWithFooter
  (style CardContent
         {:padding-bottom "0px"
          :padding-top "2px"
          :overflow "auto"
          :height "calc(100% - 35px)"}))

(def ^:const CardContentThin
  (style CardContent
         {:padding-top "2px"
          :overflow "auto"}))

(def ^:const CardActionsThin
  (style CardActions
         {:padding-bottom "0px"
          :padding-top "0px"}))

(def ^:const IconButtonSmall
  (style IconButton {:padding "5px"}))

(def ^:const TypographyNoWrap
  (style Typography {:white-space "nowrap"}))

(def ^:const TypographyFloatRight
  (style Typography {:float "right"}))

(def ^:const TypographyH5Point5
  (style Typography {:font-size "1.9rem"}))

(def ^:const GridCenter
  ;; Customize to get more than 2 cards on a screen
  (style Grid {:text-align "center"}))

(defn clock []
  [:> FixedHeightCard
   [:> CardContentThin
    [:> TypographyNoWrap {:align "center" :variant "h4"}
     @(re-frame/subscribe [::clock/day])]
    [:> TypographyNoWrap {:align "center" :variant "h1"}
     @(re-frame/subscribe [::clock/time])]
    [:> Grid {:container true :spacing 0 :direction "row"
              :justify "center" :alignItems "center"}
     [:> Grid {:item true :xs 6}
      [:> Typography {:align "center" :variant "h6"}
       @(re-frame/subscribe [::clock/time-pt])]
      [:> Typography {:align "center" :variant "body2" :color "textSecondary"}
       "San Francisco"]]
     [:> Grid {:item true :xs 6}
      [:> Typography {:align "center" :variant "h6"}
       @(re-frame/subscribe [::clock/time-ct])]
      [:> Typography {:align "center" :variant "body2" :color "textSecondary"}
       "Chicago"]]]
    (let [{:keys [sunrise sunset]} @(re-frame/subscribe [::weather/sun])]
      [:> Typography {:align "center"
                      :variant "h6"}
       [:i {:class (str "wi wi-sunrise " (:accent-color classes))}]
       sunrise
       (gstring/unescapeEntities "&#8194;")
       [:i {:class (str "wi wi-sunset " (:accent-color classes))}]
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

(defn stock-chart [symbol]
  (let [data         (-> @(re-frame/subscribe [::stocks/stocks-sparkline])
                         symbol
                         clj->js)
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
        :style (:reference-line styles)}]]]))

(defn stocks []
  [:> FixedHeightCard
   [:> CardContentWithFooter
    (into
     [:<>]
     (vec (map (fn [sym] [stock-chart (keyword sym)]) config/stocks)))]
   [:> CardActionsThin
    [:> IconButtonSmall
     {:on-click
      (fn [_]
        (doseq [sym config/stocks]
          (re-frame/dispatch [::stocks/fetch-stocks sym])))}
     [:> RefreshIcon]]
    [:div {:class (:right-aligned classes)}
     [:div
      [:> Typography {:variant "body2" :color "textSecondary"}
       @(re-frame/subscribe [::stocks/stocks-update-time])]]]]])

(defn weather-description []
  (let [{:keys [humidity feels-like description rain snow]}
        @(re-frame/subscribe [::weather/conditions])]
    (->> [{:content description :render? description}
          {:prefix "Feels like "
           :content [:span {:class (:accent-color classes)}
                     feels-like]
           :render? true}
          {:content [:span {:class (:accent-color classes)}
                     humidity [:i {:class "wi wi-humidity"}]]
           :render? true}
          {:postfix " rain"
           :content [:span {:class (:accent-color classes)} rain]
           :render? rain}
          {:postfix " snow"
           :content [:span {:class (:alert-color classes)} snow]
           :render? snow}]
         (map (fn [{:keys [prefix postfix content render?]}]
                (if render?
                  (->> [prefix content postfix] (remove nil?) vec)
                  [])))
         (remove empty?)
         (interpose [" | "])
         (apply concat [:> Typography {:align "center" :color "textSecondary"
                                       :variant "body1"}])
         vec)))

(defn weather-conditions []
  [:> Grid {:container true :spacing 0 :direction "row"
            :justify "center" :alignItems "center"}
   [:> Grid {:item true :xs 3}
    [:> Typography {:variant "h1"}
     [:i {:class (str "wi wi-"
                      @(re-frame/subscribe [::weather/icon])
                      " "(:highlight-color classes))}]]]
   [:> GridCenter {:item true :xs 5}
    [:> Typography {:align "center" :variant "h1"
                    :display "inline"}
     (:temp @(re-frame/subscribe [::weather/conditions]))]]
   [:> Grid {:item true :xs 2}
    (let [{:keys [low high]} @(re-frame/subscribe [::weather/conditions])]
      [:> TypographyFloatRight {:align "right" :display "inline" :variant "h4"}
       high [:br] low])]])

(defn weather-forecast []
  [:> Grid {:container true :spacing 1 :direction "row"
            :justify "center" :alignItems "flex-start"}
   (map
    (fn [{:keys [epoch weekday icon high low rain snow]}]
      ^{:key epoch}
      [:> Grid {:item true :xs 2}
       [:> Typography {:key epoch
                       :variant "body1"
                       :align "center"}
        weekday]
       [:> Typography {:align "center" :variant "h5"}
        [:i {:class (str "wi wi-" icon " " (:accent-color classes))}]]
       [:> Typography {:align "center" :variant "subtitle2"}
        high
        (gstring/unescapeEntities "&#8194;")
        [:span {:class (:text-secondary classes)} low]
        [:span {:class (:accent-color classes)}
         (when rain
           [:<> [:br] (list " " rain)])]
        [:span {:class (:alert-color classes)}
         (when snow
           [:<>
            (list " " snow)])]]])
    @(re-frame/subscribe [::weather/forecast]))])

(defn weather []
  [:> FixedHeightCard
   [:> CardContentWithFooter
    [weather-description]
    [weather-conditions]
    [weather-forecast]]

   [:> CardActionsThin
    [:> IconButtonSmall
     {:on-click
      (fn [_] (re-frame/dispatch [::weather/fetch-weather]))}
     [:> RefreshIcon]]
    [:div {:class (:right-aligned classes)}
     [:div
      [:> Typography {:variant "body2" :color "textSecondary"}
       @(re-frame/subscribe [::weather/weather-update-time])]]]]])

(defn covid []
  [:> FixedHeightCard
   [:> CardContentThin
    (when-let [data @(re-frame/subscribe [::covid/covid-rows])]
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
  [:> FixedHeightCard
   [:> CardContentWithFooter
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
                     [:> TypographyH5Point5
                      {:variant "h5"}
                      [:span {:style {:color (if (> minutes 0) "black" "green")}}
                       (if (> minutes 0)
                         [:<> minutes
                          [:span
                           {:class (:text-secondary classes)} "m "]]
                         "Now ")]])]]))
              (take 4))

         [:> Grid {:item true :xs 1}]])
      @(re-frame/subscribe [::transit/stop-times-processed]))]]

   [:> CardActionsThin
    [:> IconButtonSmall
     {:on-click
      (fn [_]
        (re-frame/dispatch-sync [::transit/clear])
        (doseq [event (transit/generate-events
                       config/transit-stop-whitelist)]
          (re-frame/dispatch event)))}
     [:> RefreshIcon]]
    [:div {:class (:right-aligned classes)}
     [:div
      [:> Typography {:variant "body2" :color "textSecondary"}
       @(re-frame/subscribe [::transit/stop-times-update-interval])]]]]])

(defn main-panel []
  (let [card-opts {:item true :xs 12 :sm 12 :md 6  :lg 4}]
    [:> CssBaseline
     [:> Container {:maxWidth false
                    :style {:padding-top "5px"}}
      [:> Grid {:container true :spacing 1}

       [:> Grid card-opts [weather]]

       [:> Grid card-opts [clock]]

       [:> Grid card-opts [transit]]

       [:> Grid card-opts [stocks]]

       [:> Grid card-opts [covid]]]]]))
