(ns chipper.state.save-load
  (:require [clojure.string :refer [split-lines]]
            [chipper.state.audio :refer [create-audio-context]]
            ; [chipper.chips :as c]
            [chipper.constants :as const]
            [chipper.utils :as u]
            [reagent.core :as r]
            [cljs.core.async :refer [chan]]
            [cljs.core.async :refer [<! >! close! timeout] :as async])
  (:require-macros [cljs.core.async.macros :refer [go go-loop]]))

;; TODO this is in the wrong place
(defn set-frame-used?! [frame state]
  "If :frame is used, mark it as such in :state."
  (swap! state assoc-in
         [:used-frames frame]
         (some identity
               (sequence (comp cat cat)
                         ((:slices @state) frame)))))

(defn empty-frames []
  (vec (repeat const/max-line-count (vec (repeat 4 [nil nil nil])))))

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

;; need to write a spec for this at some point
(defn serialize-frames
  "Format is:
  one frame per line
  each slice is a sequence of four notes
  each note is a sequence of 3 characters
   - note is 0-11 inclusive; o for :off, s for :stop
   - octave is 0-9 inclusiv
   - gain is 0-9 inclusive"
  [slices]
  (apply str (interpose "\n" (map serialize-slice slices))))

(defn deserialize-slice [serialized-slice]
  (vec
   (for [[note- octave- gain-] (partition 3 serialized-slice)]
     (let [note   (cond
                    (= "-" note-) nil
                    (= "o" note-) :off
                    (= "s" note-) :stop
                    :else (js/parseInt note- 16))
           octave (cond
                    (= "-" octave-) nil
                    :else (js/parseInt octave-))
           gain   (cond
                    (= "-" gain-) nil
                    :else (js/parseInt gain-))]
        ; if both nil, just give nil insead of [nil nil] for note
       [(when-not (nil? (or note octave)) [note octave]) gain nil]))))

(defn deserialize-frames [serialized-slices]
  (into [] (map deserialize-slice (split-lines serialized-slices))))

(defn serialize-compressed [frames]
  (js/LZString.compressToBase64 (serialize-frames frames)))

;; need these paginated ones for now just so we can convert old saves over
;; not sure what that means
(defn deserialize-frame [serialized-frame]
  (vec (map deserialize-slice (partition (* 4 3) serialized-frame))))

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
      (deserialize-compressed (saved-frame-state))
      (empty-frames))
    (catch js/Error e
      (do (js/alert "Error recovering from local storage. Try loading a savefile.")
          (prn e)
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
