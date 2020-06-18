(ns cockpit.events
  (:require
   [ajax.core :as ajax]
   [cockpit.config :as config]
   [cockpit.db :as db]
   [day8.re-frame.http-fx]
   [day8.re-frame.tracing :refer-macros [fn-traced]]
   [re-frame.core :as re-frame]))


(re-frame/reg-event-db
 ::initialize-db
 (fn-traced [_ _]
            db/default-db))

(defn explode-nested
  "Explode the values from nested seqs into multiple collections"
  [coll]
  (reduce (fn [coll el]
            (if (sequential? el)
              (for [c coll e el]
                (conj c e))
              (map (fn [c] (conj c el)) coll)))
          [[]]
          coll))

(defn assoc-in-all
  [m key-paths v]
  (if (sequential? key-paths)
    (reduce (fn [m key-path]
              (assoc-in m key-path v))
            m
            (explode-nested key-paths))
    (assoc m key-paths v)))

(re-frame/reg-event-db
 ::http-success
 (fn [db [_ key-path result]]
   (assoc-in-all db key-path result)))

(re-frame/reg-event-db
 ::http-fail
 (fn [db [_ key-path]]
   (assoc-in db key-path {})))
