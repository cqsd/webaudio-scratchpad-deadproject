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

(defn save-state [state]
  (.setItem js/localStorage "state" @state))

(defn bounded-add [maximum & args]
  ;; no sanity check on args because i'm the one using this fn
  ;; also is there a better way to do this because this seems excessive
  (let [acc (reduce + args)]
    (if (neg? acc) 0
      (if (>= acc maximum) maximum acc))))

(defn normalize-digit
  "This is hardcoded because the entire domain and range are known..."
  [digit]
  (or (get [0 0.11 0.22 0.33 0.44 0.55 0.66 0.77 0.88 1] digit)
      0.55)) ;; arbitrary
