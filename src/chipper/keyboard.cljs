;; This is a big mess of spaghetti. yum yum yum
(ns chipper.keyboard
  (:require [chipper.utils :refer [bounded-add]]
            [cljs.core.async :refer [put! pipe chan close! timeout]]
            [goog.events :as events]))

; ---------------------------------------------------------------------
; MAPPINGS -- Maybe these should be moved into a config file? ---------
; ---------------------------------------------------------------------
(def change-mode-mappings
  ;:Escape    :normal   ;; implicit
  {:KeyI      :edit
   :KeyV      :v-block  ;; not implemented
   :ShiftKeyV :v-line}) ;; non-standard

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
    {:edit-attr
     (merge
       {:KeyA :C   :ShiftKeyA :C# :KeyW :C#
        :KeyS :D   :ShiftKeyS :D# :KeyE :D#
        :KeyD :E
        :KeyF :F   :ShiftKeyF :F# :KeyT :F#
        :KeyG :G   :ShiftKeyG :G# :KeyY :G#
        :KeyH :A   :ShiftKeyH :A# :KeyU :A#
        :KeyJ :B
        :KeyX :off
        :Backspace :backspace}

       ;; {:Digit0 :Digit0...} yeah, this is a really fucking bad hack my god
       ;; but i mean... it's static! so...
       (apply hash-map
              (mapcat identity
                      (for [n (range 10)
                            :let [s (keyword (str "Digit" n))]]
                       [s s]))))}))

(def normal-dispatch-mappings
  (merge-with
    conj
    common-mappings
    {:relative-movement
     {:KeyJ :down-jump
      :KeyK :up-jump
      :KeyH :left-attr
      :KeyL :right-attr

      :KeyW :right-chan
      :KeyB :left-chan

      :ShiftBracketRight :down-beat
      :ShiftBracketLeft  :up-beat}

     :absolute-movement
     {:ShiftKeyG   :last-line
      :KeyG        :first-line
      :ShiftDigit4 :last-chan
      :Digit0      :first-chan}

     :edit-attr
     {:KeyX :delete}}))

(def internal-values
  (merge
    {;; relative movement
     ;; :code [d-line d-chan d-attr]
     ;; the nils are a hack to make logic easier...
     ;; I mean, this whole solution is a stupid hack but
     :down-line    [1 nil 0] :up-line    [-1 nil 0]
     :right-attr   [0 nil 1] :left-attr  [0 nil -1]
     :right-chan   [0 1 0]   :left-chan  [0 -1 0]
     :down-beat    [4 nil 0] :up-beat    [-4 nil 0]

     ;; delete note
     ;; :code     [[note octave] pre-move post-move]
     :off       [[:off nil] nil :down-line]
     :delete    [[nil nil] nil :up-line]
     :backspace [[nil nil] :up-line :up-line]

     :oh-god
     {:delete [nil nil :up-line]
      :backspace [nil :up-line :up-line]}}

    ;; {:Digit0 0..:Digit9 9}
    (apply hash-map
           (mapcat identity
                   (for [n (range 10)] [(keyword (str "Digit" n)) n])))))

(def -movement-keys
  #{:Space :ArrowDown :ArrowUp :ArrowLeft :ArrowRight :Tab})


; ---------------------------------------------------------------------
; HANDLERS ------------------------------------------------------------
; ---------------------------------------------------------------------
(defn absolute-movement-handler
  [internal-key _ context]
  (let [position (case internal-key
                   :last-line  [(dec (count (:slices @context)))]
                   :first-line [0]
                   :first-chan [nil 0 0]
                   :last-chan  [nil (dec (count (:schema @context)))])]
    {:set-position position}))

(defn relative-position
  "Takes movement code (:down-line, :down-beat, etc), returns vector of
  next position."
  [internal-key [line chan attr :as active-position] context]
  (let [state         @context
        move-vector   (get internal-values internal-key internal-key)
        [dline
         dchan        ;; this is really fuckin savage
         dattr]       (case move-vector
                        :up-jump [(- (:jump-size state)) 0 0]
                        :down-jump [(:jump-size state) 0 0]
                        (or move-vector [0 0 0]))
        ;; since dattr is signed, dchan can just take its value when crossing over
        next-attr-    (+ attr dattr)
        dchan         (or dchan
                          (if (or (neg? next-attr-) (> next-attr- 2)) dattr 0))
        next-line     (bounded-add (dec (count (:slices state))) line dline)
        next-chan     (bounded-add (dec (count (:schema state))) chan dchan)
        next-attr     (mod next-attr- 3)]
    [next-line next-chan next-attr]))

(defn relative-movement-handler
  [internal-key active-position context]
  (let [next-position (relative-position internal-key active-position context)]
    {:set-position next-position}))

;; refactorable
(defn edit-note-handler
  [internal-key [line chan attr :as active-position] context]
  (let [[note- pre-move post-move-] (get internal-values internal-key)
        ;; this or might be unnecessary
        note (or note- [internal-key (:octave @context)])
        post-move (or post-move- :down-jump)
        pre-move-position (relative-position pre-move active-position context)
        post-move-position (relative-position post-move active-position context)]
    {:set-attr [note (or pre-move-position active-position)]
     :set-position post-move-position}))

;; refactorable
(defn edit-other-attr-handler
  "Only acts on numbers right now, which is... just as well."
  [internal-key active-position context]
  (let [[value- pre-move post-move-] (get-in internal-values [:oh-god internal-key])
        ;; this is intentional-------------v
        value (or value-
                  (when-let [spaghetti (get internal-values internal-key)]
                    (when-not (vector? spaghetti) spaghetti)))
        post-move (or post-move- :down-jump)
        pre-move-position (relative-position pre-move active-position context)
        post-move-position (relative-position post-move active-position context)]
    {:set-attr [value pre-move-position]
     :set-position post-move-position}))

(defn edit-attr-handler
  [internal-key [_ __ attr :as active-position] context]
  (prn attr)
  (let [handler (if (zero? attr) edit-note-handler edit-other-attr-handler)]
    (handler internal-key active-position context)))

(defn edit-octave-handler [octave _ context]
  (let [current (:octave @context)
        doctave (case octave
                  :up-octave (if (> 12 current) 1 0)
                  :down-octave (if (pos? current) -1 0)
                  0)]
    {:set-octave (+ current doctave)}))

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
  (let [k-of-matching-m (fn [[k m]] ;; closure over this---v
                          (when-let [internal-key (get m keycode)]
                            [k internal-key ]))
        [dispatch-key
         internal-key] (some k-of-matching-m dispatch-mappings)]
    (when dispatch-key [dispatch-key internal-key])))

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

(defn set-attr! [directive context]
  (when (:set-attr directive) ;; when-let doesn't work for destructuring
    (let [[value position] (:set-attr directive)
          [line chan attr] position]
      (swap! context update-in [:slices line chan]
             #(assoc % attr value)))))

(defn set-octave! [directive context]
  (when-let [octave (:set-octave directive)]
    (swap! context assoc :octave octave)))

(defn set-position! [directive context]
  (let [[line chan attr] (:set-position directive)]
    (swap! context assoc
           :active-line (or line (:active-line @context))
           :active-chan (or chan (:active-chan @context))
           :active-attr (or attr (:active-attr @context)))))

(defn active-position [context]
  (let [state @context]
    [(:active-line state) (:active-chan state) (:active-attr state)]))

(defn handle-keypress!
  "Translates event keycode into directives, then acts on the directives."
  [ev context]
  (when-let [keycode (-> ev
                         (prevent-movement!)
                         (translate-keycode)
                         (maybe-change-mode! context))]
    (let [active-position   (active-position context)
          dispatch-mappings (case (:mode @context)
                              :normal normal-dispatch-mappings
                              :edit insert-dispatch-mappings)
          [dispatch-key
           internal-key]    (dispatch-info keycode dispatch-mappings)
          handler           (case dispatch-key
                              :relative-movement relative-movement-handler
                              :absolute-movement absolute-movement-handler
                              :edit-attr         edit-attr-handler
                              :edit-octave       edit-octave-handler
                              (constantly nil))
          directives        (handler internal-key active-position context)]
      (prn dispatch-key)
      (prn directives)
      ;; this feels temporary but deep down i know i'm just going to keep it
      (set-attr! directives context)
      (set-octave! directives context)
      (set-position! directives context))))

;; TODO this needs to go somewhere else (should the chan also handle mousdown?)
(defn init-keycode-chan! []
  (let [ch (chan)]
    (.addEventListener ;; XXX is this really the best we can do
      js/window
      "keydown"
      (fn [ev]
        (put! ch (keyword (.-code ev))))) ;; key code is agnostic of kb layout
    ch))
