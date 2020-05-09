(ns cockpit.views
  (:require
   [re-frame.core :as re-frame]
   [cockpit.subs :as subs]
   ["@material-ui/core" :refer [Button Card CardActionArea CardActions
                                CardContent CardMedia Container Grid
                                CssBaseline Paper Typography ThemeProvider]]
   ["@material-ui/core/styles" :refer [makeStyles]]))


(defn clock []
  [:> Card {:style {:height "100%"}}
   (let [current-time @(re-frame/subscribe [::subs/time])]
     [:> CardContent
      [:> Typography {:align "center" :variant "h1" :style {:font-size "6vw"}}
       (.toLocaleTimeString current-time [] #js {:hour "numeric" :minute "numeric" :hour12 true})]
      [:> Typography {:align "center" :variant "h2" :style {:font-size "3vw"}}
       (.toLocaleDateString current-time [] #js {:weekday "long" :month "long" :day "numeric"})]])])

(defn cute []
  [:> Card  {:style {:height "100%"}}
   [:> CardActionArea
    [:> CardMedia
     {:image "https://icatcare.org/app/uploads/2018/07/Thinking-of-getting-a-cat.png"
      :style {:height "300px"}}]]
   [:> CardContent "efghi"]])


(defn main-panel []
  [:> CssBaseline
   [:> Container {:maxWidth false}
    [:> Grid {:container true :spacing 1}
     [:> Grid {:item true :xs 4} [clock]]
     [:> Grid {:item true :xs 4} [cute]]
     [:> Grid {:item true :xs 4}
      [:> Card  {:style {:height "100%"}}
       [:> CardContent "jklmnop"]]]
     [:> Grid {:item true :xs 4}
      [:> Card {:style {:height "100%"}}
       [:> CardContent "qrstuvwxyz"]
       [:> CardContent "qrstuvwxyz"]
       [:> CardContent "qrstuvwxyz"]
       [:> CardContent "qrstuvwxyz"]
       [:> CardContent "qrstuvwxyz"]
       [:> CardContent "qrstuvwxyz"]]]]]])
