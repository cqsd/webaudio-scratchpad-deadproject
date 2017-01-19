;; there's a lot of just... data in this file
;; a lot of it is not strictly necessary; it's just for convenience
;; XXX the semantics of internal mappings is inconsistent
(ns chipper.keyboard
  (:require [cljs.core.async :refer [put! pipe chan close! timeout]]
            [goog.events :as events])
  (:require-macros [cljs.core.async.macros :refer [go-loop]]))

(defn -to-internal [event-keycode mappings]
  ;; includes pass-through for things that already have good-enough key-codes
  ;; to use as internal values
  (get (:event mappings) event-keycode event-keycode))

; ---------------------------------------------------------------------
; MAPPINGS ------------------------------------------------------------
; ---------------------------------------------------------------------

(def change-mode-mappings
  {;:Escape    :normal
   :KeyI      :insert
   :KeyR      :replace-one
   :KeyV      :v-block  ;; non-standard
   :ShiftKeyR :replace-many
   :ShiftKeyV :v-line})

(def insert-mappings
  {:event
   {:KeyA :C   :ShiftKeyA :C# :KeyW :C#
    :KeyS :D   :ShiftKeyS :D# :KeyE :D#
    :KeyD :E
    :KeyF :F   :ShiftKeyF :F# :KeyT :F#
    :KeyG :G   :ShiftKeyG :G# :KeyY :G#
    :KeyH :A   :ShiftKeyH :A# :KeyU :A#
    :KeyJ :B

    :KeyX :off

    :Equal :upoctave
    :Minus :downoctave}

   :internal  ;; lmk if there's a better way to do this bullshit
   {:C 0 :C# 0 :D 0 :D# 0 :E 0 :F 0 :F# 0 :G 0 :G# 0
    :A 1 :A# 1 :B 1}})

(def movement-mappings
  ;; TODO jump keys (g, 0, etc)
  {:event  ;; event mappings take event data -> internal rep
   {:KeyJ :down-line    :ArrowDown  :down-line
    :KeyK :up-line      :ArrowUp    :up-line
    :KeyH :left-attr    :ArrowLeft  :left-attr
    :KeyL :right-attr   :ArrowRight :right-attr

    :Tab      :right-chan   :KeyW :right-chan
    :ShiftTab :left-chan    :KeyB :left-chan

    :ShiftBracketRight :down-measure
    :ShiftBracketLeft  :up-measure}

   :internal  ;; internal mappings take internal -> whatever is needed for use?
   {:down-line    [1 nil 0] :up-line    [-1 nil 0]
    :right-attr   [0 nil 1] :left-attr  [0 nil -1]
    :right-chan   [0 1 0]   :left-chan  [0 -1 0]
    :down-measure [4 nil 0] :up-measure [-4 nil 0]}})

(def -movement-keys
  (apply hash-set :Space :ShiftSpace (keys (:event movement-mappings))))

; ---------------------------------------------------------------------
; HANDLERS ------------------------------------------------------------
; ---------------------------------------------------------------------

;; XXX keycode needs to be a keyword...
(defn movement-handler [internal-code context]
  (let [state         @context
        active-line   (:active-line state)
        active-chan   (:active-chan state)
        active-attr   (:active-attr state)
        [dline
         dchan
         dattr]       (get (:internal movement-mappings) internal-code [0 0 0])
        ;; since dattr is signed, dchan can just take its value when crossing over
        dchan         (or dchan
                          (if-not (some #{(+ active-attr dattr)} (range 0 3))
                            dattr 0))
        next-line     (mod (+ active-line dline) (count (:slices state)))
        next-chan     (mod (+ active-chan dchan) (count (:schema state)))
        next-attr     (mod (+ active-attr dattr) 3)]
    (prn [internal-code dline])
    (swap! context assoc
           :active-line next-line
           :active-chan next-chan
           :active-attr next-attr)))

(defn -set-note-and-move [note octave line chan context]
  (swap! context update-in [:slices line chan]
         #(assoc %
                 0 note
                 1 octave))
  (movement-handler :down-line context))

(defn -note-handler [note-code context])
(defn -octave-handler [octave-code context])

(defn insert-handler [internal-code context]
  ;; this one is really bad
  (let [state @context
        [line chan attr] (vals (select-keys
                                 state
                                 [:active-line :active-chan :active-attr]))]
    (if (= :off internal-code)
      (-set-note-and-move :off nil line chan context)
      (when-let [octave-offset (and (zero? attr)
                                    (get (:internal insert-mappings) internal-code))]
        (-set-note-and-move internal-code
                   (+ octave-offset (:octave state))
                   line chan context)))))

(defn maybe-change-mode [keycode context]
  (if (= :Escape keycode)
    (do (swap! context assoc :mode :normal) nil)
    (if-let [mode (and (= :normal (:mode @context))
                       (get change-mode-mappings keycode))]
      (do (swap! context assoc :mode mode) nil)
      keycode)))

(defn dispatcher [ev context]
  ;; there should just be a map of movement key codes, I think.
  (when (some #{(keyword (.-code ev))} -movement-keys)
    (.preventDefault ev))
  (let [keycode- (if (.-shiftKey ev)
                   (keyword (str "Shift" (.-code ev)))
                   (keyword (.-code ev)))
        keycode  (maybe-change-mode keycode- context)]
    (case (:mode @context)
      :normal (movement-handler (-to-internal keycode movement-mappings) context)
      :insert (insert-handler (-to-internal keycode insert-mappings) context)
      :replace-one nil
      :replace-many nil
      :v-line nil
      :v-block nil)))

;; TODO this needs to go somewhere else (should the chan also handle mousdown?)
(defn init-keycode-chan! []
  (let [ch (chan)]
    (.addEventListener ;; XXX is this really the best we can do
      js/window
      "keydown"
      (fn [ev]
        (put! ch (keyword (.-code ev))))) ;; key code is agnostic of kb layout
    ch))
