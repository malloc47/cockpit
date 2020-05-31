(ns cockpit.core
  (:require
   [reagent.core :as reagent]
   [reagent.dom :as rdom]
   [re-frame.core :as re-frame]
   [cockpit.events :as events]
   [cockpit.views :as views]
   [cockpit.config :as config]
   [cockpit.polling :as poll-config]
   [re-pollsive.core :as poll]))


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
  (doseq [[alias {:keys [route-id]}] config/transit-stops]
    (re-frame/dispatch [::events/fetch-transit-route route-id alias]))
  (dev-setup)
  (mount-root))
