(ns cockpit.core
  (:require
   [cockpit.config :as config]
   [cockpit.events :as events]
   [cockpit.polling :as poll-config]
   [cockpit.views :as views]
   [re-frame.core :as re-frame]
   [re-pollsive.core :as poll]
   [reagent.dom :as rdom]))


(defn dev-setup []
  (when config/debug?
    (println "dev mode")))

(defn ^:dev/after-load mount-root []
  (re-frame/clear-subscription-cache!)
  (let [root-el (.getElementById js/document "app")]
    (rdom/unmount-component-at-node root-el)
    (rdom/render [views/main-panel] root-el)))

(defn init []
  (re-frame/dispatch-sync [::events/initialize-db])
  (re-frame/dispatch-sync [::poll/init])
  (re-frame/dispatch [::poll/set-rules poll-config/rules])
  (dev-setup)
  (mount-root))
