(ns chipper.utils
  (:require [cljs.core.async :refer [<! >! chan close! timeout]])
  (:require-macros [cljs.core.async.macros :refer [go]]))

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
  "I have no clue why this is called what it's called and it should be
  renamed. Honestly, I don't think this even needs to be its own function;
  it's used once, and specifically tailored for the gain channel. Maybe I'll
  call it `map-gain` or something. Who knows!"
  [digit]
  ;; arbitrary multiplier because oscillators are loud and this works through
  ;; my headphones
  (* 0.05 (or (get [0 0.11 0.22 0.33 0.44 0.55 0.66 0.77 0.88 1] digit)
              0.55))) ;; arbitrary
