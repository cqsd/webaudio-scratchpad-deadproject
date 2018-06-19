(ns chipper.utils
  (:require [cljs.core.async :refer [<! >! chan close! timeout]]
            [clojure.string :refer [split-lines]]
            [chipper.notes :as notes])  ; not good to require this in utils
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defn reset-cursor! [state]
  "Set the cursor position to top left."
  (swap! state assoc
         :active-line 0
         :active-chan 0
         :active-attr 0))

(defn set-frame-used?! [frame state]
  "If :frame is used, mark it as such in :state."
  (swap! state assoc-in
         [:used-frames frame]
         (some identity
               (sequence (comp cat cat)
                         ((:slices @state) frame)))))

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
  (apply str
         (for [[[note octave] gain _] slice]
           (let [notestr   (cond
                             (nil? note) "-"
                             (= :off note) "o"
                             (= :stop note) "s"
                             :else (.toString (note notes/note-steps) 16))
                 octavestr (or octave "-")
                 gainstr   (or gain "-")]
             (str notestr octavestr gainstr)))))

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
  (vec
    (for [[note- octave- gain-] (partition 3 serialized-slice)]
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
  (vec (map deserialize-slice (partition (* 4 3) serialized-frame))))

(defn deserialize-frames [serialized-frames]
  (into [] (map deserialize-frame (split-lines serialized-frames))))

(defn serialize-compressed [frames]
  (js/LZString.compressToBase64 (serialize-frames frames)))

(defn deserialize-compressed [compressed]
  (deserialize-frames (js/LZString.decompressFromBase64 compressed)))

(defn save-local! [state]
  (try
    (do (.setItem js/localStorage
             "state"
             (serialize-compressed (:slices @state)))
        (swap! state assoc :frame-edited nil))
    (catch js/Error e
      (prn "Couldn't save to local storage."))))

(defn saved-frame-state [] (.getItem js/localStorage "state"))

(defn empty-frames []
  (vec (repeat 32 (vec (repeat 32 (vec (repeat 4 [nil nil nil])))))))

(defn recover-frames-or-make-new! []
  (try
    (if-let [saved (saved-frame-state)]
      (let [found (deserialize-compressed (saved-frame-state))]
        (if (= 32 (count found))
          found
          (throw (js/Error. "Bad save"))))
      (empty-frames))
    (catch js/Error e
      (do (js/alert "Error recovering from local storage. Try loading a savefile.")
          (empty-frames)))))

(defn set-frames! [frames state]
  (swap! state assoc :slices frames))

(defn set-used-frames! [frames state]
  (doseq [x (range (count (:used-frames @state)))]
    (set-frame-used?! x state)))

(defn save! [state]
  (let [compressed (serialize-compressed  (:slices @state))
        data-url   (str "data:application/octet-stream," compressed)]
    (save-local! state)
    (try
      (do (prn data-url)
        (js/window.open data-url "save")
          (swap! state assoc :frame-edited nil))
      (catch js/Error e
        (js/alert "Error saving. Tough luck.")))))

; TODO XXX ERROR HANDLING
(defn set-frame-state-from-b64 [state s]
  (swap! state assoc :slices (deserialize-compressed s))
  (doseq [x (range (count (:used-frames @state)))]
    (set-frame-used?! x state)))

(defn load-save-file! [state evt]
  (try
    (let [file   (aget (.-files (.-target evt)) 0)
          reader (js/FileReader.)]
      (set! (.-onload reader) #(set-frame-state-from-b64 state (.-result reader)))
      (.readAsText reader file))
    (catch js/Error e
      (js/alert "Unable to load from file. Tragic :/"))))

(defn check-set-frame-use [state]
  ; (prn (str "frame edited" (:frame-edited @state)))
  (when (:frame-edited @state)
    (set-frame-used?! (:active-frame @state) state)
    (swap! state assoc
           :frame-edited nil)))

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
  (* 0.05 (or (get [0 0.11 0.22 0.33 0.44 0.55 0.66 0.77 0.88 1] digit)
             0.55))) ;; arbitrary
