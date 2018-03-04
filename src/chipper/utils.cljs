(ns chipper.utils
  (:require [cljs.core.async :refer [<! >! chan close! timeout]])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defn reset-cursor! [state]
  (swap! state assoc
         :active-line 0
         :active-chan 0
         :active-attr 0))

;; FIXME this shouldn't be in utils
(defn delayed-chan
  "http://ku1ik.com/2015/10/12/sweet-core-async.html"
  [coll interval]
  (let [ch (chan)]
    (go
      (loop [coll coll]
        (when (seq coll)
          (>! ch (first coll))
          ; parked ('blocking') take from an empty channel which
          ; closes ('releases') after `interval` ms
          (<! (timeout interval))
          (recur (rest coll)))
        (close! ch)))
    ch))

(defn serialize-state
  ;; TODO
  [state])

(defn deserialize-editor [saved-state])

(defn save-state [state]
  (.setItem js/localStorage "state" @state))

(defn enumerate
  ([coll]
   (enumerate coll 0))
  ([coll start]
   (map vector coll (map (partial + start) (range)))))

(defn bounded-add
  "Should be named better. Bounded non-negative addition"
  [maximum & args]
  (min maximum (max 0 (reduce + args))))

(defn normalize-digit
  "This is hardcoded because the entire domain and range are known..."
  [digit]
  (or (get [0 0.11 0.22 0.33 0.44 0.55 0.66 0.77 0.88 1] digit)
      0.55)) ;; arbitrary
