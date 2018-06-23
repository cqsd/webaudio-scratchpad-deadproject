;; TODO it would feel more natural to pass state as the first argument to each
;; of these fns rather than the last
(ns chipper.actions
  (:require [clojure.string :refer [split-lines]]
            [chipper.constants :as const]
            [chipper.chips :as c]
            [chipper.keyboard2 :as k]
            [chipper.state :as s]
            [chipper.utils :as u]))

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
  "Frame setter. Prefer using this over swap!-ing the state atom directly."
  [frame state]
  (swap! state assoc :active-frame frame))

(defn set-attr!
  "Attribute setter. Prefer using this over swap!-ing the state atom directly."
  [[line chan attr] frame value state]
  (swap! state update-in [:slices frame line chan]
         #(assoc % attr value)))

;; canned state operations
(defn reset-cursor! [state]
  "Set the cursor position to top left."
  (set-cursor-position! [0 0 0] state))

(defn set-absolute-position!
  "bit of potentially confusing naming, given the existence of set-cursor-position!"
  [params state]
  (js/alert "not implemented!"))

(defn set-relative-position!
  "This is still a mess, sort of"
  [motion state]
  ;; ever heard of select-keys?
  (let [[dline dchan dattr] (const/motions motion)
        [line chan attr] (s/cursor-position state)
        next-line (u/bounded-add (dec const/frame-length) line dline)
        ;; use this until the composer state representation gets fixed...
        cursor-attr-pos (+ (* const/attr-count chan) attr)
        ;; a plain mod of attr + dattr would cause the cursor to cycle in the first
        ;; and last channel positions, so this is one way to solve that
        next-attr- (u/bounded-add (dec const/total-attr-positions) cursor-attr-pos dattr)
        next-attr (mod next-attr- const/attr-count)
        next-chan (quot next-attr- const/attr-count)]
    (set-cursor-position! [next-line next-chan next-attr] state)))

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
        [_ _ attr :as position] (s/cursor-position state)
        ;; so for now, if the cursor's on the note, we insert [notename octave]
        ;; otherwise we insert NOTHING
        playable (not (or (nil? value-) (#{:off :stop} value-)))
        value (when (zero? attr)
                (if playable
                  [value- (:octave @state)]
                  [value- nil nil]))]
    (set-attr! position frame value state)
    ;; so we're carrying over garbage from the previous implementation
    (when playable (c/play-slice! state (:player @state) position)))
  ;; and this means set-attr-at-cursor! could either be a macro, or,,,,,
  (set-relative-position! (:down-line const/motions) state))

(defn set-octave!
  [params state]
  (js/alert "not implemented!"))

(defn play-pause! [_ state] ; uh oh
  (c/play-track state (:player @state)))

;; you've never seen ugly code before
(declare handle-property!)

(defn macro!
  [actions state]
  (doseq [[property params] actions]
    ;; this is happening here instead of pushing onto the actions queue because
    ;; all the macro actions need to happen *now*, not after the stuff already
    ;; on the queue gets done
    (handle-property! property params state)))

(def property-handlers
  {:mode set-mode!
   :motion set-relative-position!  ;; FIXME naming
   :absolute-position set-absolute-position!
   :frame set-relative-frame!
   :attr set-attr-at-cursor!
   :octave set-octave!
   :play-pause play-pause!
   :macro macro!})

(defn handle-property!
  "Find and call the proper handler"
  [property params state]
  (when-let [handler (property-handlers property)]
    (handler params state)))

;; event handlers
(defn handle-mousedown!
  "Handles the mousedown event, which doesn't need to go through the keymappings."
  [ev state]
  (let [id (.-id (.-target ev))
        [_ literal-chan _ :as id-data] (.split id "-")
        [line chan attr :as parsed-id] (map js/parseInt id-data)]
    (when (every? number? parsed-id)  ;; This indicates the user clicked in the main area.
      (set-cursor-position! parsed-id state))
    (when (= "f" literal-chan)  ;; This indicates the user clicked on a page.
      (swap! state assoc
             :active-frame line)
      (s/set-frame-used?! (:active-frame @state) state))))

(def -movement-keys
  #{:Space :ArrowDown :ArrowUp :ArrowLeft :ArrowRight :Tab :Backspace})

(defn -prevent-movement! [ev]
  "Prevent movement keys (arrows, space, tab) from moving focus or scrolling."
  (when (-movement-keys (keyword (.-code ev)))
    (.preventDefault ev))
  ev)

(defn -translate-keycode [ev]
  "Takes keydown event and returns keycode as keyword; e.g., :KeyI, :Digit0.
  If SHIFT is held, prepend Shift; e.g., :ShiftKeyI.
  If CTRL is held, prepend Ctrl; e.g., :CtrlShiftKeyI."
  (keyword (str (when (.-ctrlKey ev) "Ctrl")
                (when (.-shiftKey ev) "Shift")
                ;; .-code is nil if not a keypress
                (.-code ev))))

(defn handle-keypress!
  "Translate the keycode, get the property and params out of the top level
  keymap, trigger the top level handler"
  [ev state]
  (when-let [keycode (-> ev -prevent-movement! -translate-keycode)]
    (let [mode              (:mode @state)
          [property params] (get-in k/mode-keymaps [mode keycode])]
      (handle-property! property params state))))
