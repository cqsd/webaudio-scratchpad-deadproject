(ns chipper.state
  (:require [clojure.string :refer [split-lines]]
            [cljs.core.async :refer [chan]]
            [reagent.core :as r]
            [chipper.audio :refer [create-audio-context]]
            [chipper.constants :as const]
            [chipper.utils :as u]))

(defn empty-frames []
  (vec (repeat const/max-line-count (vec (repeat 4 [nil nil nil])))))

;; some explanation is due
;; this documentation is written is very after-the-fact, so no types because i
;; don't heckin remember them
;; - scheme - a vector of WebAudio oscillator types [:sine :triangle ...]
;; - chip - a vector of WebAudio oscillators, basically. I don't remember
;; - note - a vector of [note-name volume unused]
;;   - attr[ibute] - index into a note. might be better to do something a little
;;     more OOP-like with real fields and getters and setters. maybe use to
;;     represent notes instead of plain vectors? I dunno, haven't thought about
;;     it
;; - slice - a vector of notes. represents a single line in the editor
;;   - chan[nel] - the column representing notes for a given oscillator;
;;     the index in a slice vector (so in a scheme of [:sine :sawtooth], the
;;     :sine channel is slice[0] and :sawtooth is slice[1])
;; - frame - 32 slices. think of it like a "page"; it used to actually be
;;   a vector of 32 slices, and the editor state was a vector of frames
;;   (the whole editor state was "paginated"), but now it's just a logical
;;   concept that's used to highlight stuff in the high-level view to the
;;   right of the track
;; - track - a vector of slices containing the entire state of the editor;
;;   represents your composition as a whole
;; - player - a map of the audio context, the chip, the core.async channel
;;   used to get nonblocking playback, the scheme, and some other hax
(def state
  (r/atom
   {:slices (empty-frames)
    :active-line 0
    :active-chan 0
    :active-attr 0
    :active-frame 0
    :frame-edited nil
    ;; TL new
    ;; the state handlers (should) set this to true if you make any change
    ;; the save handler (should) then set this to false on save
    :track-edited? nil
    :used-frames (vec (repeat const/frame-count nil)) ; for indicating on the right
    :octave 4
    :bpm 100
    :mode :normal
    :player {:audio-context (create-audio-context)
             :chip nil
             :track-chan nil
             ;; XXX hax. when a note is entered, its whole slice is pushed to
             ;; `note-chan` and is immediately consumed/played by `note-chip`
             :note-chip nil
             :note-chan (chan 2)
             :scheme [:square :square :triangle :sawtooth]}}))

(defn get-player
  [state attr]
  (get-in @state [:player attr]))

(defn update-player
  [state attr value]
  (swap! state update-in [:player attr] (constantly value)))

(defn cursor-position [state]
  [(:active-line @state)
   (:active-chan @state)
   (:active-attr @state)])

;; primitive editor state operations
;; prefer using these over swap!-ing the state atom directly
(defn set-mode!
  [mode state]
  (swap! state assoc :mode mode))

(defn set-cursor-position!
  [[line chan attr] state]
  (swap! state assoc
         :active-line line
         :active-chan chan
         :active-attr attr))

(defn set-frame!
  [frame state]
  ;; gonna feel good to delete the active-frame line when we
  ;; stop tracking active frame separately...
  (let [active-line (min const/max-line-count (* frame const/frame-length))
        ;; this is a bug by the way, it should be more like.
        ;; (round (/ ...)) but I don't care, as long as I can
        ;; see what im doing >.>
        ]
    (swap! state assoc
           :active-line active-line
           :active-frame frame)
    (prn @state)))

(defn set-attr!
  [[line chan attr] _frame value state]
  (swap! state update-in [:slices line chan]
         #(assoc % attr value)))

(defn set-octave! [octave state]
  (swap! state assoc :octave octave))

(defn set-bpm! [bpm state]
  (swap! state assoc :bpm bpm))

;; canned state operations
(defn reset-cursor! [state]
  "Set the cursor position to top left."
  (set-cursor-position! [0 0 0] state))

(defn set-absolute-position!
  "Potentially confusing naming, given the existence of set-cursor-position!"
  [params state]
  (js/alert "not implemented!"))

(defn set-relative-position!
  "This is still a mess."
  [motion state]
  (let [[dline dchan dattr] (const/motions motion)
        [line chan attr] (cursor-position state)
        next-line (u/bounded-add const/last-position line dline)
        ;; use this until the composer state representation gets fixed...
        cursor-attr-pos (+ (* const/attr-count chan) attr)
        ;; a plain mod of attr + dattr would cause the cursor to cycle in the first
        ;; and last channel positions, so this is one way to solve that
        next-attr- (u/bounded-add (dec const/total-attr-positions) cursor-attr-pos dattr)
        next-attr (mod next-attr- const/attr-count)
        ;; re confusion in constants: here it is...
        ;; fix this
        next-chan (quot next-attr- const/attr-count)]
    (set-cursor-position! [next-line next-chan next-attr] state)))

(defn set-frame-used?! [frame state]
  "If :frame is used, mark it as such in :state."
  (swap! state assoc-in
         [:used-frames frame]
         (some identity
               (sequence (comp cat cat)
                         ((:slices @state) frame)))))

;; TODO set the frame-edited flag in state
(defn set-relative-frame!
  [dframe state]
  (let [frame (:active-frame @state)]
    (set-frame!
     (u/bounded-add (dec const/frame-count) frame dframe)
     state)))

(defn set-attr-at-cursor!
  ;; so value-'ll get renamed next patch to actually be meaninful, re set-attr!
  [value- state]
  (let [frame (:active-frame @state)
        ;; mhm, mhm, yeah, this is a thing that will change next patch
        [_ _ attr :as position] (cursor-position state)
        ;; so for now, if the cursor's on the note, we insert [notename octave]
        ;; otherwise we insert NOTHING
        playable (not (or (nil? value-) (#{:off :stop} value-)))
        value (if (zero? attr)
                (if playable
                  [value- (:octave @state)]
                  [value- nil nil])
                value-)]
    (set-attr! position frame value state)
    (comment (when playable (c/play-slice! state (:player @state) position)))))

(defn set-relative-octave!
  [direction state]
  (set-octave!
   (+ (:octave @state) (const/-garbage direction))
   state))

(defn set-relative-bpm!
  [direction state]
  (set-bpm!
   (+ (:bpm @state) (const/-garbage direction))
   state))

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

;; need to write a spec for this at some point
(defn serialize-frames
  "Format is:
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
