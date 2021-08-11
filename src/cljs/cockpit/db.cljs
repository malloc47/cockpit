(ns cockpit.db)

(def default-db
  {:clock            (js/Date.)
   :stocks           {}
   :weather          {}
   :webcam           {:url nil}
   :covid            nil
   :transit          {:stops        {}
                      :stop-times   {}
                      :routes       {}
                      :update-times {}}})
