(ns chipper.state
  (:require [clojure.string :refer [split-lines]]
            [reagent.core :as r]
            [chipper.audio :refer [create-audio-context]]
            [cljs.core.async :refer [chan]]))

(defn empty-frames []
  (vec (repeat 32 (vec (repeat 32 (vec (repeat 4 [nil nil nil])))))))

(def state
  (r/atom
    {:slices (empty-frames)
     :active-line 0
     :active-chan 0
     :active-attr 0
     :active-frame 0
     :frame-edited nil
     :used-frames (vec (repeat 32 nil)) ; for indicating on the right
     :octave 4
     :bpm 100
     :mode :normal
     :player {:audio-context (create-audio-context)
              :chip nil
              :track-chan nil
              :note-chip nil  ; for playing single notes when keys are pressed
              :note-chan (chan 2)  ; sigh ; 18jun18 what the fuck is
              :scheme [:square :square :triangle :sawtooth]}}))

(defn get-player
  [state attr]
  (get-in @state [:player attr]))

(defn update-player
  [state attr value]
  (swap! state update-in [:player attr] (constantly value)))

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
                             :else (.toString note 16))
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
   - note is 0-11 inclusive; o for :off, s for :stop
   - octave is 0-9 inclusiv
   - gain is 0-9 inclusive"
  [frames]
  (apply str (interpose "\n" (map serialize-frame frames))))

(defn deserialize-slice [serialized-slice]
  (vec
    (for [[note- octave- gain-] (partition 3 serialized-slice)]
      (let [note    (cond
                      (= "-" note-) nil
                      (= "o" note-) :off
                      (= "s" note-) :stop
                      :else (js/parseInt note- 16))
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
