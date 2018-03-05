(ns chipper.utils
  (:require [cljs.core.async :refer [<! >! chan close! timeout]]
            [clojure.string :refer [split-lines]]
            [chipper.notes :as notes])  ; not good to require this in utils
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

(defn nonempty-frames [state]
  (keep-indexed
    (fn [i v] (when v (get-in @state [:slices i])))
    (:used-frames @state)))

(defn serialize-slice [slice]
  (for [[[note octave] gain _] slice]
    (let [notestr   (cond
                      (nil? note) "-"
                      (= :off note) "o"
                      (= :stop note) "s"
                      :else (.toString (note notes/note-steps) 16))
          octavestr (or octave "-")
          gainstr   (or gain "-")]
      (str notestr octavestr gainstr))))

(defn serialize-frame [frame]
  (apply str (mapcat serialize-slice frame)))

(defn serialize-frames
  "Format is:
  version on first line, length 4 (%0.01)
  For Version 0.01:
  one frame per line
  each slice is a sequence of four notes
  each note is a sequence of 3 characters
   - note is 0-B (0-11) inclusive; o for :off, s for :stop
   - octave is 0-9 inclusiv
   - gain is 0-9 inclusive"
  [frames]
  (apply str (interpose "\n" (map serialize-frame frames))))

(def note-reverse-map (zipmap (vals notes/note-steps) (keys notes/note-steps)))

(defn deserialize-slice [serialized-slice]
  (into [] (for [[note- octave- gain-] (partition 3 serialized-slice)]
     (let [note    (cond
                     (= "-" note-) nil
                     (= "o" note-) :off
                     (= "s" note-) :stop
                     :else (note-reverse-map (js/parseInt note- 16)))
           octave  (cond
                     (= "-" octave-) nil
                     :else (js/parseInt octave-))
           gain    (cond
                     (= "-" gain-) nil
                     :else (js/parseInt gain-))]
       ; if both nil, just give nil insead of [nil nil] for note
       [(when-not (nil? (or note octave)) [note octave]) gain nil]))))

(defn deserialize-frame [serialized-frame]
  (into [] (map deserialize-slice (partition (* 32 4 3) serialized-frame))))

(defn deserialize-frames [serialized-frames]
  (into [] (map deserialize-frame (split-lines serialized-frames))))

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
