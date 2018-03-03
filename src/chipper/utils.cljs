(ns chipper.utils
  (:require [cljs.core.async :refer [<! >! chan close! timeout]])
  (:require-macros [cljs.core.async.macros :refer [go]]))

;; FIXME this shouldn't be in utils
(defn delayed-coll-chan
  ;; TODO this is eventually gonna need a slight tweak when we implement
  ;; the tempo change directive
  ;; XXX when browser is not focused, timeouts only fire 1x per second at max
  "With thanks to Marcin Kulik for this post:
  http://ku1ik.com/2015/10/12/sweet-core-async.html"
  [coll interval]
  (let [ch (chan)]
    (go
      (loop [coll coll]
        (when (seq coll)
          (>! ch (first coll))
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

(defn enumerate [coll]
  (map vector coll (range)))

(defn bounded-add
  "Should be named better. Bounded non-negative addition"
  [maximum & args]
  (min maximum (max 0 (reduce + args))))

(defn normalize-digit
  "This is hardcoded because the entire domain and range are known..."
  [digit]
  (or (get [0 0.11 0.22 0.33 0.44 0.55 0.66 0.77 0.88 1] digit)
      0.55)) ;; arbitrary
