(ns cockpit.db)

(def default-db
  {:time (js/Date.)
   :stocks {}
   :weather {}
   :covid nil})
