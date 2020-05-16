(ns cockpit.views
  (:require
   [re-frame.core :as re-frame]
   [cockpit.subs :as subs]
   [cockpit.weather :as weather]
   [goog.string :as gstring]
   ["@material-ui/core" :refer [Button Card CardActionArea CardActions
                                CardContent CardMedia Container Grid
                                CssBaseline Paper Typography ThemeProvider]]
   ["react-sparklines" :refer [Sparklines SparklinesLine SparklinesReferenceLine dataProcessing]]
   ["@material-ui/core/styles" :refer [makeStyles]]
   [clojure.string :as str]))


(defn clock []
  [:> Card {:style {:height "100%"}}
   [:> CardContent
    [:> Typography {:align "center" :variant "h1" :style {:font-size "6vw"}}
     @(re-frame/subscribe [::subs/time])]
    [:> Typography {:align "center" :variant "h2" :style {:font-size "3vw"}}
     @(re-frame/subscribe [::subs/day])]]])

(defn cute []
  [:> Card  {:style {:height "100%"}}
   [:> CardActionArea
    [:> CardMedia
     {:image "https://icatcare.org/app/uploads/2018/07/Thinking-of-getting-a-cat.png"
      :style {:height "300px"}}]]
   [:> CardContent "efghi"]])

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
    (- 50 (* (/ (- ref bottom) (- top bottom)) 50))))

(defn round-number
   [f]
  (/ (.round js/Math (* 100 f)) 100))

(defn stock-chart [symbol]
  (let [data         (clj->js (symbol @(re-frame/subscribe [::subs/stocks])))
        diff         (round-number (- (last data) (first data)))
        up?          (>= (last data) (first data))
        diff-display (str (if (>= diff 0) "+" "") diff)
        percent      (round-number (* (- 1 (/ (first data) (last data))) 100))
        color        (if up? "green" "red")]
    (if (> (count data) 1)
      [:div
       [:> Typography {:variant "body1"
                       :display "inline"
                       :style {:color "rgba(0, 0, 0, 0.62)"}}
        (str (name symbol) " ")]
       [:> Typography {:variant "h6"
                       :display "inline"
                       :style {:color "rgba(0, 0, 0, 0.87)"}}
        (last data)]
       [:> Typography {:variant "body1"
                       :display "inline"
                       :style {:color "rgba(0, 0, 0, 0.62)"}}
        " USD "]
       [:> Typography {:variant "body1"
                       :display "inline"
                       :style {:color color}}
        (str diff-display " (" percent "%) " (if up? "▲" "▼"))]
       [:> Sparklines {:data (or data []) :height 50 :margin 0}
        [:> SparklinesLine {:color color}]
        [:> SparklinesReferenceLine
         {:type "custom" :value (correct-reference-line data)
          :style {:stroke "black"
                  :strokeOpacity 0.75
                  :strokeDasharray "1, 3" }}]]]
      [:div "This shouldn't happen"])))

(defn weather [symbol]
  (let [weather @(re-frame/subscribe [::subs/weather])]
    [:div
     [:> Typography {:variant "h5"
                     :display "inline"
                     :style {:color "rgba(132, 132, 132)"}}
      "New York, NY"]
     [:> Typography {:align "center" :variant "h1" :style {:font-size "6vw"}}
      [:i {:class (str "wi wi-" (weather/request->icon weather))}]
      (str " " (-> weather :current :temp int) "°")]
     [:> Typography {:align "center" :display "block" :variant "h2" :style {:font-size "3vw"}}
      (let [temps (-> weather :daily first :temp )]
        (str (-> temps :min int) "°" (gstring/unescapeEntities "&#8194;") (-> temps :max int) "°"))]]))

(defn main-panel []
  [:> CssBaseline
   [:> Container {:maxWidth false}
    [:> Grid {:container true :spacing 1}

     [:> Grid {:item true :xs 4}
      [:> Card  {:style {:height "100%"}}
       [:> CardContent [weather]]]]

     [:> Grid {:item true :xs 4} [clock]]
     [:> Grid {:item true :xs 4}
      [:> Card  {:style {:height "100%"}}
       [:> CardContent
        [stock-chart :UNH]
        [stock-chart :GRPN]]]]
     [:> Grid {:item true :xs 4}
      [:> Card {:style {:height "100%"}}
       [:> CardContent "Empty"]]]]]])
