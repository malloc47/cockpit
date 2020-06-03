(ns cockpit.db)

(def default-db
  {:clock   (js/Date.)
   :stocks  {}
   :weather {}
   :covid   nil
   :transit {:stop-times {}
             :routes     {}}})
