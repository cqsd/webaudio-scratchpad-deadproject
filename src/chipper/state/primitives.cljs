;; TODO this might actually merit another subdirectory, because the audio.cljs
;; file is all "primitives" too. Or maybe we should refine what primitive means
;; I don't like that primitives is importing from state lmfao but this is a pretty
;; haphazard intermediate step...
(ns chipper.state.primitives
  (:require [clojure.string :refer [split-lines]]
            [chipper.state.audio :refer [create-audio-context]]
            ;; [chipper.state.player :as c]
            [chipper.constants :as const]
            [chipper.utils :as u]
            [cljs.core.async :refer [chan]]
            [reagent.core :as r]
            [cljs.core.async :refer [<! >! close! timeout] :as async])
  (:require-macros [cljs.core.async.macros :refer [go go-loop]]))

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

;; looks like it's finna be time to break this up soon lol
(def state
  (r/atom
   {:slices nil
    :active-line 0
    :active-chan 0
    :active-attr 0
    :view-start 0
    :view-end   const/view-size
    :view-size  const/view-size
    ; :active-frame 0
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
             ;; `preview-chan` and is immediately consumed/played by `preview-chip`
             ;; i guess we'll fix this when we rip it into a state machine, ...
             ;; pls gib nrg to actually make these changes
             :preview-chip nil
             :preview-chan (async/chan 2)
             :scheme [:square :square :triangle :sawtooth]}}))

(defn get-player
  [state attr]
  (get-in @state [:player attr]))

(defn update-player
  [state attr value]
  (swap! state update-in [:player attr] (constantly value)))

;; rename to like, play-at-position (and idfk how the hell the refactor will work)
(defn push-slice-at-position
  [state player [line _ _]]
  (let [ch            (:preview-chan (:player @state))
        chip          (get-player state :preview-chip)
        slice         (get-in @state [:slices line])
        scheme-length (count (get-player state :scheme))]
    ;; put the slice on the channel, put a delay spacer to let the note play,
    ;; put an :off directive to shut off playback. effect is we'll just play
    ;; one slice
    ;; (prn (str "pushing " slice " at " line " onto preview channel"))
    ;; (prn (str "scheme length " scheme-length))
    (go (>! ch slice)
        (<! (timeout 100)) ; hack! you'll have to use the app to see what's wrong, it's too much to explain
        ;; put enough :off directives to turn off all notes (hacky!)
        (>! ch (vec (repeat scheme-length [[:off nil] nil nil]))))))

(defn cursor-position [state]
  [(:active-line @state)
   (:active-chan @state)
   (:active-attr @state)])

;; primitive editor state operations
;; prefer using these over swap!-ing the state atom directly
(defn set-mode!
  [mode state]
  (swap! state assoc :mode mode))

(defn bound-checked-line-number
  "Return a line number that's within the bounds of the track"
  [line state]
  (let [last-position-in-track (dec (count (:slices @state)))]
    (max (min line last-position-in-track) 0)))

(defn next-view-boundaries
  "Return new view boundaries in the form [view-start view-end], taking
  into account the scrolloff setting and the current viewport size"
  [next-line state]
  ;; note that we have to take the direction of travel of the cursor into account
  ;; when computing view-size, but we set the absolute position in this function
  ;; (ie, we don't have a sign to rely on to determine direction of travel)
  ;;
  ;; thus the logic is
  ;;  - get the current boundaries
  ;;  - if next position > (view-end - scrolloff),
  ;;      adjust view-end to be position + 2 (bound-checked)
  ;;      adjust view-start to be view-end - view-size
  ;;  - if next position < (view-start + scrolloff),
  ;;      adjust view-start to be position - 2 (bound-checked)
  ;;      adjust view-start to be view-end + view-size
  ;;  - otherwise we're somewhere in the middle, so just return the existing ones
  ;; we could write this in a more clever way but this is clearest
  ;; note that this is probably gonna slow rendering the fuck down lmfao
  (let [start (:view-start @state)
        end   (:view-end @state)
        size  (:view-size @state)]
    ; (prn next-line)
    ; (prn [start end])
    (cond
      ;; scrolling down beyond the margin
      (>= next-line (- end const/scrolloff))
      ;; note this inc: it's because bound-checked.. is really for ensuring that
      ;; the next _cursor position_ is on the track, ie, it's one less than the
      ;; "logical line number" (ie, it's an index to the track).
      ;; view-end is used to take a slice of the track using subvec, so it has to
      ;; be one greater than the last index we want, ie, we have to inc the result
      ;; from bound-checked-linue-number (yes, it's a hack)
      (let [next-end (inc (bound-checked-line-number (+ const/scrolloff next-line) state))
            next-start (- next-end const/view-size)]
        [next-start next-end])
      ;; scrolling up
      (< next-line (+ start const/scrolloff))
      (let [next-start (bound-checked-line-number (- next-line const/scrolloff) state)
            next-end (+ next-start const/view-size)]
        [next-start next-end])
      :else [start end])))

;; XXX this is a hack imo
(defn set-cursor-line!
  "used by the player to advance the cursor to follow along with playback"
  [line state]
  ;; ensure that we can't set the cursor position beyond the bounds of
  ;; the track (notice the position in the line is missing a check..)
  (let [bound-checked-line (bound-checked-line-number line state)
        [view-start view-end] (next-view-boundaries bound-checked-line state)]
    (swap! state assoc
           :active-line bound-checked-line
           :view-start  view-start
           :view-end    view-end)))

(defn set-cursor-position!
  [[line chan attr] state]
  ;; ensure that we can't set the cursor position beyond the bounds of
  ;; the track (notice the position in the line is missing a check..)
  (let [bound-checked-line (bound-checked-line-number line state)
        [view-start view-end] (next-view-boundaries bound-checked-line state)]
    (swap! state assoc
           :active-line bound-checked-line
           :active-chan chan
           :active-attr attr
           :view-start  view-start
           :view-end    view-end)))

(defn set-attr!
  [[line chan attr] value state]
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

(defn set-relative-position!
  "This is still a mess."
  [motion state]
  (let [[dline dchan dattr] (const/motions motion)
        [line chan attr] (cursor-position state)
        next-line (u/bounded-add (count (:slices @state)) line dline)
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

(defn set-attr-at-cursor!
  ;; so value-'ll get renamed next patch to actually be meaninful, re set-attr!
  ;; explanation for now: each note is a vec like [[:A 4] 1 1]; each of those vector
  ;; positions is an "attr", meaning attr may refer to [note octave], the dynamic,
  ;; or the effect (unimplemented) respectively. we'll have to change this when
  ;; we switch this over to being a .MOD player/tracker...
  [value- state]
  (let [;; mhm, mhm, yeah, this must change soon TODO
        [_ _ attr :as position] (cursor-position state)
        ;; so for now, if the cursor's on the note, we insert [notename octave]
        ;; otherwise we insert NOTHING
        playable (not (or (nil? value-) (#{:off :stop} value-)))
        value (if (zero? attr)
                (if playable
                  [value- (:octave @state)]
                  [value- nil nil])
                value-)]
    (set-attr! position value state)
    (when playable
      (push-slice-at-position state (:player @state) position))))

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

; (defn check-set-frame-use [state]
;   ; (prn (str "frame edited" (:frame-edited @state)))
;   (when (:frame-edited @state)
;     (set-frame-used?! (:active-frame @state) state)
;     (swap! state assoc
;            :frame-edited nil)))
