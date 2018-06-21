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

;; XXX These should maybe be somwhere else, like core, or... something
;; also note that if you use these, the max index of the cursor is 1 less
;; because position is 0-indexed
(def frame-count 32)
(def frame-length 32)
(def chan-count 4)
(def attr-count 3)
;; a hack, because of course
(def total-attr-positions (* chan-count attr-count))

(def -motions
  {:down  [ 1  0  0]
   :up    [-1  0  0]
   :left  [ 0  0 -1]
   :right [ 0  0  1]})

(def normal-keymap
  {;; relative movement
   ;; keeping :position for clarity. The representation for cursor position may
   ;; change in the future, thus -motions
   :KeyJ        [:relative-position (:down  -motions)]
   :KeyK        [:relative-position (:up    -motions)]
   :KeyH        [:relative-position (:left  -motions)]
   :KeyL        [:relative-position (:right -motions)]

   :KeyEnter    [:relative-position (:down  -motions)]
   :ArrowDown   [:relative-position (:down  -motions)]
   :ArrowUp     [:relative-position (:up    -motions)]
   :ArrowLeft   [:relative-position (:left  -motions)]
   :ArrowRight  [:relative-position (:right -motions)]

   :KeyW        [:relative-position [measure-size     0 0]]
   :KeyB        [:relative-position [(- measure-size) 0 0]]

   :ShiftBracketRight [:frame  1]
   :ShiftBracketLeft  [:frame -1]

   ;; absolute movement
   :KeyG        [:absolute-position [:first :first :first]]
   :ShiftKeyG   [:absolute-position [:last  :last  :last]]
   :Digit0      [:absolute-position [:first :first :first]]
   :ShiftDigit4 [:absolute-position [:last  :last  :last]]

   ;; mode change
   :KeyI [:mode :edit]
   :KeyV [:mode :visual]

   ;; other
   :KeyX        [:macro {:type :simple :value [[:attr nil] (:down-line -motions)]}]
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

   :Backspace [:macro {:type :simple :value [[:attr nil] (:up-line -motions)]}]
   :Space     [:relative-position (:down-line -motions)]})

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

(defn set-position!
  "Cursor position setter. Prefer using this over swap!-ing the state
  atom directly"
  [[line chan attr] state]
  (swap! state assoc
         :active-line line
         :active-chan chan
         :active-attr attr))

(defn set-relative-position!
  "This is still a mess, sort of"
  [[dline dchan dattr] state]
  ;; ever heard of select-keys?
  (let [line (:active-line @state)
        chan (:active-chan @state)
        attr (:active-attr @state)
        next-line (u/bounded-add (dec frame-length) line dline)
        ;; TODO temporary, hopefully :)
        ;; for now, we'll use cursor-attr-pos for these next two until the
        ;; composer state representation gets fixed...
        cursor-attr-pos (+ (* attr-count chan) attr)
        ;; a plain mod of attr + dattr would cause the cursor to cycle in the first
        ;; and last channel positions, so this is one way to solve that
        next-attr- (u/bounded-add (dec total-attr-positions) cursor-attr-pos dattr)
        next-attr (mod next-attr- attr-count)
        next-chan (quot next-attr- attr-count)]
    (set-position! [next-line next-chan next-attr] state)))

;; it's just gotta map the keywords to the numbahz
;; and that's just because all the bounds checking happens here; the ui just
;; silently fails to render the cursor (and probably other things) if you give
;; invalid indices
(defn set-absolute-position!
  "bit of potentially confusing naming, given the existence of set-position!"
  [params state]
  (js/alert "not implemented!"))

(defn set-frame!
  "Frame setter. Prefer using this over swap!-ing the state atom directly."
  ;; I hate that I'm using the word setter and defining functions called set-*
  [frame state]
  (swap! state assoc
         :active-frame frame))

;; TODO set frame-edited
(defn set-relative-frame!
  "Frame setter. Prefer using this over swap!-ing the state atom directly."
  ;; I hate that I'm using the word setter and defining functions called set-*
  [dframe state]
  (let [frame (:active-frame @state)]
    (set-frame!
      (u/bounded-add (dec frame-count) frame dframe)
      state)))

(defn set-attr!
  "Attribute setter. Prefer using this over swap!-ing the state atom directly."
  [params state])

(defn set-attr-at-cursor!
  [params state])

(defn macro!
  "it's gonna have to handle :simple and :complex macros"
  [params state]
  (js/alert "not implemented!"))

(defn special!
  [params state]
  (js/alert "not implemented!"))

(def handler-map
  {:relative-position set-relative-position!
   :absolute-position set-absolute-position!
   :frame set-relative-frame!
   :attr set-attr-at-cursor!
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
