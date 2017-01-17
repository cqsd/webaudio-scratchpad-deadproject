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
