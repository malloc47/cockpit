(ns cockpit.utils
  (:require
   [cljs-time.core :as time]
   [clojure.string :as str]))

(defn round
  [num & [decimals]]
  (let [base (->> (repeat 10) (take (or decimals 2)) (reduce *))]
    (/ (.round js/Math (* base num)) base)))

(defn safe-interval
  "The local clock and the OTP instance clock are not guaranteed to be
  in sync, and in practice the OTP instance provides times ahead of
  local clock. Instead of blowing up, this swallows these errors."
  [a b]
  (try
    (time/interval a b)
    (catch js/Object e
      (time/interval b b))))

(defn format-interval
  [interval]
  (let [base-seconds (or (some-> interval time/in-seconds) 0)
        seconds      (mod base-seconds 60)
        minutes      (-> base-seconds (/ 60) (mod 60) int)
        hours        (-> base-seconds (/ (* 60 60)) int)]
    (or
     (->> [hours minutes seconds]
          (drop-while zero?)
          reverse
          (map #(str %2 %1) ["s" "m" "h"])
          reverse
          (str/join " ")
          not-empty)
     "0s")))
