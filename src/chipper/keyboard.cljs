;; I think I'm one level too deep with the maps.
(ns chipper.keyboard
  (:require [cljs.core.async :refer [put! pipe chan close! timeout]]
            [goog.events :as events])
  (:require-macros [cljs.core.async.macros :refer [go-loop]]))

; ---------------------------------------------------------------------
; MAPPINGS ------------------------------------------------------------
; ---------------------------------------------------------------------

(def change-mode-mappings
  {:KeyI      :edit
   ; :KeyR      :replace
   :KeyV      :v-block  ;; non-standard
   ; :ShiftKeyR :replace-many
   :ShiftKeyV :v-line})

(def common-mappings
  {:relative-movement
   {:ArrowDown  :down-line
    :ArrowUp    :up-line
    :ArrowLeft  :left-attr
    :ArrowRight :right-attr
    :Tab        :right-chan
    :ShiftTab   :left-chan}
  :edit-octave
  {:Equal      :up-octave
   :Minus      :down-octave
   :Space      :up-octave
   :ShiftSpace :down-octave
   :Slash      :up-octave
   :Period     :down-octave
   :ShiftEqual :swap-up-octave
   :ShiftMinus :swap-down-octave}})

(def insert-dispatch-mappings
  (merge-with
    conj
    common-mappings
    {:edit-note
     {:KeyA :C   :ShiftKeyA :C# :KeyW :C#
      :KeyS :D   :ShiftKeyS :D# :KeyE :D#
      :KeyD :E
      :KeyF :F   :ShiftKeyF :F# :KeyT :F#
      :KeyG :G   :ShiftKeyG :G# :KeyY :G#
      :KeyH :A   :ShiftKeyH :A# :KeyU :A#
      :KeyJ :B
      :KeyX :off
      :Backspace :backspace}}))

(def normal-dispatch-mappings
  (merge-with
    conj
    common-mappings
    {:relative-movement
     {:KeyJ :down-line
      :KeyK :up-line
      :KeyH :left-attr
      :KeyL :right-attr

      :KeyW :down-measure
      :KeyB :up-measure

      :ShiftBracketRight :down-measure
      :ShiftBracketLeft  :up-measure}

     :absolute-movement
     {:ShiftKeyG   :last-line
      :KeyG        :first-line
      :ShiftDigit4 :last-chan
      :Digit0      :first-chan}

     :edit-note
     {:KeyX :delete}}))

(def internal-value-mappings
  {:relative-movement
   {:down-line    [1 nil 0] :up-line    [-1 nil 0] ;; these nils are a hack
    :right-attr   [0 nil 1] :left-attr  [0 nil -1]
    :right-chan   [0 1 0]   :left-chan  [0 -1 0]
    :down-measure [4 nil 0] :up-measure [-4 nil 0]
    :down-jump :down-jump :up-jump :up-jump}

   :absolute-movement
   #{:last-line :first-line :first-chan :last-chan}

   :edit-note ;; octave offsets
   #{:C :C# :D :D# :E :F :F# :G :G# :A :A# :B
     :off
     :delete
     :backspace}

   :edit-octave
   #{:up-octave :down-octave}})

(def -movement-keys
  #{:Space :ArrowDown :ArrowUp :ArrowLeft :ArrowRight :Tab})


; ---------------------------------------------------------------------
; HANDLERS ------------------------------------------------------------
; ---------------------------------------------------------------------
(defn set-position! [[line chan attr] context]
  (swap! context assoc
         :active-line (or line (:active-line @context))
         :active-chan (or chan (:active-chan @context))
         :active-attr (or attr (:active-attr @context))))

(defn absolute-movement-handler [_ position- context]
  (let [position (case position-
                   :last-line  [(dec (count (:slices @context)))]
                   :first-line [0]
                   :first-chan [nil 0 0]
                   :last-chan  [nil (dec (count (:schema @context)))])]
    (set-position! position context)))

(defn bounded-add [maximum & args]
  ;; no sanity check on args because i'm the one using this fn
  ;; also is there a better way to do this because this seems excessive
  (let [acc (reduce + args)]
    (if (neg? acc) 0
      (if (>= acc maximum) maximum acc))))

(defn relative-movement-handler [_ move-vector context]
  (let [state         @context
        active-line   (:active-line state)
        active-chan   (:active-chan state)
        active-attr   (:active-attr state)
        [dline
         dchan        ;; this is really fuckin savage
         dattr]       (case move-vector
                        :up-jump [(- (:jump-size @context)) 0 0]
                        :down-jump [(:jump-size @context) 0 0]
                        (or move-vector [0 0 0]))
        ;; since dattr is signed, dchan can just take its value when crossing over
        next-attr-    (+ active-attr dattr)
        dchan         (or dchan
                          (if (or (neg? next-attr-) (> next-attr- 2)) dattr 0))
        next-line     (bounded-add (dec (count (:slices state))) active-line dline)
        next-chan     (bounded-add (dec (count (:schema state))) active-chan dchan)
        next-attr     (mod next-attr- 3)]
    (prn [move-vector dchan dline])
    (set-position! [next-line next-chan next-attr] context)))

(defn set-note! [note octave line chan context]
  (swap! context update-in [:slices line chan]
         #(assoc % 0 note 1 octave)))

(defn set-relative-position! [direction context]
  (relative-movement-handler
      direction
      (get (:relative-movement internal-value-mappings) direction)
      context))

(defn -set-note-and-move [note octave line chan direction context]
  (set-note! note octave line chan context)
  (set-relative-position! direction context))

;; next two fns are really spaghetti... well, really, this whole file is spaghetti
(defn edit-note-handler [note octave-offset context]
  (let [state @context
        line  (:active-line state)
        chan  (:active-chan state)
        attr  (:active-attr state)]
    (case note
      :off (-set-note-and-move :off nil line chan :down-line context)
      :delete (-set-note-and-move nil nil line chan :down-line context)
      :backspace (do (set-relative-position! :up-line context)
                     (insert-note-handler :delete nil context))
      ;; static analysis doesn't like this when-let
      ;(when-let [octave-offset (and (zero? attr) octave-offset-)]
      (when (and (zero? attr) octave-offset)
        (-set-note-and-move note (+ octave-offset (:octave state))
          line chan :down-jump context)))))

(defn edit-other-attr-handler
  "Only acts on hex. Doesn't need per-attr dispatch at this point."
  [value ])

(defn edit-attr-handler [value octave-offset context]
  (let [state @context
        line  (:active-line state)
        chan  (:active-chan state)
        attr  (:active-attr state)]
  (insert-note-handler note octave-offset context)))

(defn edit-octave-handler [octave _ context]
  (let [current (:octave @context)
        doctave (case octave
                  :up-octave (if (> 12 current) 1 0)
                  :down-octave (if (pos? current) -1 0)
                  0)]
    (swap! context assoc :octave (+ current doctave))))

(defn maybe-change-mode! [keycode context]
  (if (= :Escape keycode)
    (do (swap! context assoc :mode :normal) nil)
    (if-let [mode (and (= :normal (:mode @context))
                       (get change-mode-mappings keycode))]
      (do (swap! context assoc :mode mode) nil)
      keycode)))

; ---------------------------------------------------------------------
; DISPATCHING ---------------------------------------------------------
; ---------------------------------------------------------------------
;; TODO needs reorganization

(defn dispatch-info
  "Returns the dispatch key, internal key, and internal value for the keycode"
  [keycode dispatch-mappings]
  (let [k-of-matching-m (fn [[k m]] ;; capture------------v
                          (when-let [internal-key (get m keycode)]
                            [k internal-key ]))
        [dispatch-key internal-key
         :as internal-value-position] (some k-of-matching-m dispatch-mappings)]
    (when dispatch-key
      [dispatch-key
       internal-key
       (get-in internal-value-mappings internal-value-position)])))

(def ev-dispatch-mappings
  {:normal normal-dispatch-mappings
   :edit insert-dispatch-mappings})

;; goal: dispatch by line now; the handlers shouldn't have to
;; deref to get the coordinates:w

(defn active-position [context]
  (let [state @context]
    [(:active-line state) (:active-chan state) (:active-attr state)]))

(defn prevent-movement! [ev]
  "Prevent movement keys (arrows, space, tab) from moving focus or scrolling."
  (when (-movement-keys (keyword (.-code ev)))
    (.preventDefault ev))
  ev)

(defn translate-keycode [ev]
  "Takes keydown event and returns keycode as keyword, e.g., :KeyI. If
  SHIFT is held, concats with Shift first, e.g., :ShiftKeyI."
  (if (.-shiftKey ev)
    (keyword (str "Shift" (.-code ev)))
    (keyword (.-code ev))))

(defn dispatcher
  "Translates event keycode into directives, then acts on the directives.
  Some things preventing this from being a little cleaner:
   - preventDefault and mode switching should happen first because
     - the first is necessary
     - the second allows short-circuit logic
   - mode switching is functionally different from movement and editing;
     writing it into one of the handlers just means moving the logic of
     what to do with Escape and KeyI somewhere else, so we might as well
     do it here where it's obvious."
  [ev context]
  ;; this is what happens you need some imperative clojure I guess
  (when-let [keycode (-> ev
                         (prevent-movement!)
                         (translate-keycode!)
                         (maybe-change-mode! context))]
    (let [position (active-position context)
          dispatch-mappings (get ev-dispatch-mappings (:mode @context))
          dispatch-info* (dispatch-info keycode dispatch-mappings)
          directives (as-> dispatch-info* d
                       (movement-handler d)
                       (attr-handler d))]
      (set-attr! directives context)
      (set-position directives context))))

;; TODO this needs to go somewhere else (should the chan also handle mousdown?)
(defn init-keycode-chan! []
  (let [ch (chan)]
    (.addEventListener ;; XXX is this really the best we can do
      js/window
      "keydown"
      (fn [ev]
        (put! ch (keyword (.-code ev))))) ;; key code is agnostic of kb layout
    ch))
