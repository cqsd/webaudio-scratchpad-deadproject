(ns chipper.keyboard2 ; electric booga--[being dragged away by the deep state]
  (:require [chipper.chips :as c]
            [chipper.utils :as u]
            [goog.events :as events]))

;; ok so here's the plan
;; event -> chan (queue) -> process (produce something) -> chan (queue) -> handler
;; "something" is probably just a vec? or a map?
;; like [property parameters]
;; property and parameters would be like
;; :position  [dline dchan dattr]  ; let's say numerical values + :first :last
;; :attribute [line chan attr val] ; attr is any of the three note position
;; :mode      :edit                ; :normal :edit :visual
;; (and it's called edit because it behaves like replace, which doesn't make sense
;; as a name in the absence of a distinct insert mode)
;; :special ; which is then its own thing, like {:action :octave-up :parameters 10}
;; :custom  ; or maybe `:macro`? something to facilitate more complex things
;; then a big handler decides which little handler actually alters state
;; big handler would basically just be a another map like
;; {:position  set-position!
;;  :attribute set-attribute!  ; this needs a better name than attribute, maybe
;;  etc}

;; Available properties are
;; :position :frame :mode :attr :macro :special
;; Available macro types are
;; :simple   (a sequence of position/frame/mode/attr)
;; :advanced (a callable?)
;; and macros mappings are defined as
;; {:Trigger [:macro {:type type :value value}]}
;; See edit mode :Backspace for an example

(def measure-size 4)

(def motions-
  {:down  [ 1  0  0]
   :up    [-1  0  0]
   :left  [ 0  0 -1]
   :right [ 0  0  1]})

(def normal-keymap
  {;; relative movement
   ;; keeping :position for clarity. The representation for cursor position may
   ;; change in the future, thus motions-
   :KeyJ        [:position (:down  motions-)]
   :KeyK        [:position (:up    motions-)]
   :KeyH        [:position (:left  motions-)]
   :KeyL        [:position (:right motions-)]

   :KeyEnter    [:position (:down  motions-)]
   :ArrowDown   [:position (:down  motions-)]
   :ArrowUp     [:position (:up    motions-)]
   :ArrowLeft   [:position (:left  motions-)]
   :ArrowRight  [:position (:right motions-)]

   :KeyW        [:position [ 4  0  0]]
   :KeyB        [:position [-4  0  0]]

   :ShiftBracketRight [:frame  1]
   :BracketRight      [:frame -1]

   ;; absolute movement
   :KeyG        [:position [:first :first :first]]
   :ShiftKeyG   [:position [:last  :last  :last]]
   :Digit0      [:position [:first :first :first]]
   :ShiftDigit4 [:position [:last  :last  :last]]

   ;; mode change
   :KeyI [:mode :edit]
   :KeyV [:mode :visual]

   ;; other
   :KeyX        [:macro {:type :simple :value [[:attr nil] (:down-line motions-)]}]
   ;; so these could be special or just, like... :tempo, and :octave
   ;; I'm leaving them out for now
   ;; :Period [:special [:tempo 1]]
   ;; :ShiftPeriod [:special [:tempo -1]]
   :Space [:special :play-pause]})

(def edit-keymap
  ;; hm, this isn't going to work quite like you want it to.
  ;; this is fine for inputting notes...
  {:KeyA [:attr :C]    :KeyW [:attr :C#]
   :KeyS [:attr :D]    :KeyE [:attr :D#]
   :KeyD [:attr :E]    :KeyR [:attr :F]
   :KeyF [:attr :F#]   :KeyT [:attr :G]
   :KeyG [:attr :G#]   :KeyY [:attr :A]
   :KeyH [:attr :A#]   :KeyU [:attr :B]

   ;; ... but when you get to the other attributes, what happens? They have
   ;; values in [0x00, 0xff].
   ;; You gotta either have another sort of translation layer where some of your
   ;; note names become hex digits, or... something
   ;; In fact, now that I think about it, this is why volume in the pre-alpha version
   ;; only goes up to 9.
   :Digit0 [:attr :Digit0]
   :Digit1 [:attr :Digit1]
   :Digit2 [:attr :Digit2]
   :Digit3 [:attr :Digit3]
   :Digit4 [:attr :Digit4]
   :Digit5 [:attr :Digit5]
   :Digit6 [:attr :Digit6]
   :Digit7 [:attr :Digit7]
   :Digit8 [:attr :Digit8]
   :Digit9 [:attr :Digit9]

   :KeyX      :off
   :ShiftKeyX :stop

   :Backspace [:macro {:type :simple :value [[:attr nil] (:up-line motions-)]}]
   :Space     [:position (:down-line motions-)]})

(def keymap
  {:normal normal-keymap
   :edit   edit-keymap})

(def -movement-keys
  #{:Space :ArrowDown :ArrowUp :ArrowLeft :ArrowRight :Tab :Backspace})

(defn prevent-movement! [ev]
  "Prevent movement keys (arrows, space, tab) from moving focus or scrolling."
  (when (-movement-keys (keyword (.-code ev)))
    (.preventDefault ev))
  ev)

(defn translate-keycode [ev]
  "Takes keydown event and returns keycode as keyword; e.g., :KeyI, :Digit0.
  If SHIFT is held, prepend Shift; e.g., :ShiftKeyI.
  If CTRL is held, prepend Ctrl; e.g., :CtrlShiftKeyI."
  (keyword (str (when (.-ctrlKey ev) "Ctrl")
                (when (.-shiftKey ev) "Shift")
                ;; .-code is nil if not a keypress
                (.-code ev))))

;; XXX don't know what to name these, but might not matter
(defn position!
  [params state])

(defn frame!
  [params state])

(defn attr!
  [params state])

(defn macro!
  [params state])

(defn special!
  [params state])

(def handler-map
  {:position position!
   :frame frame!
   :attr attr!
   :special special!
   :macro macro!})

(defn handle-keypress!
  "see top of file. idk, doc this if you want"
  [ev state]
  ;; when-let because it may not be a keypress. See `translate-keycode`
  ;; the order of prevent-movement! and translate-keycode maybe can be changed?
  ;; to shortcircuit a bit
  (when-let [keycode (-> ev prevent-movement! translate-keycode)]
    (let [mode              (:mode @state)
          [property params] (get-in keymap [mode keycode])]
      (when-let [handler (handler-map property)]
        (handler params state)))))
