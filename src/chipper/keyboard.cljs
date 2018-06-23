(ns chipper.keyboard
  (:require [chipper.chips :as c]
            [chipper.utils :as u]
            [chipper.utils :refer [bounded-add]]
            [goog.events :as events]))

(def change-mode-mappings
  ; :Escape    :normal   ;; implicit
  {:KeyI      :edit})
  ; :KeyV      :v-block  ;; not implemented
  ; :ShiftKeyV :v-line}) ;; non-standard

(def common-mappings
  {:relative-movement
   {:ArrowDown    :down-line
    :ArrowUp      :up-line
    :Enter        :down-line
    :ArrowLeft    :left-attr
    :ArrowRight   :right-attr
    :Tab          :right-chan
    :ShiftTab     :left-chan
    :BracketRight :right-chan
    :BracketLeft  :left-chan}

  :edit-attr
  ;; 17jun18 like what is this shit
  {:Backspace :delete-and-move-up}

  :edit-octave
  {:Equal      :up-octave
   :Minus      :down-octave
   :ShiftEqual :up-octave}

  :edit-global
  {:ShiftBracketRight :forward-frame
   :ShiftBracketLeft  :back-frame}})

;; 17jun18 what the fuck is this!
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
        :ShiftKeyX :stop
        :Backspace :delete-above}

       ;; 17jun18 what the fuck!!!!
       ;; {:Digit0 :Digit0...} yeah, this is a really bad hack my god
       (apply hash-map
              (mapcat identity
                      (for [n (range 10)
                            ;; 17jun18 WHAT
                            :let [s (keyword (str "Digit" n))]]
                       [s s]))))
     :relative-movement
     {:Space      :down-line
      :ShiftSpace :up-line}}))

(def normal-dispatch-mappings
  (merge-with
    conj  ; 17jun18 AAAAAAAAAAAAAAAAAAAAHHHHHHHHHHHHHHHHHHHH
    common-mappings
    {:relative-movement
     {:KeyJ :down-line
      :KeyK :up-line
      :KeyH :left-attr
      :KeyL :right-attr
      :KeyW :down-jump
      :KeyB :up-jump}

     :absolute-movement
     {:ShiftKeyG   :last-line
      :KeyG        :first-line
      :ShiftDigit4 :last-line
      :Digit0      :first-line}

     :edit-attr
     {:KeyX      :delete}

     :edit-global
     {:Space :play-pause}

     :edit-bpm
     {:Period      :tempo-up
      :Comma       :tempo-down
      :ShiftPeriod :tempo-up-big
      :ShiftComma  :tempo-down-big}
}))

;; 17jun18 DELETE! DELEET!!
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

     ;; delete note or turn off, format is:
     ;; :code [[note octave] pre-move post-move]
     :off                [[:off nil] nil :down-line]
     :stop               [[:stop nil] nil :down-line]
     :delete             [nil nil nil]
     :delete-above       [nil :up-line :up-line]
     :delete-and-move-up [nil nil :up-line]

     :oh-god
     {:delete       [nil nil nil]
      :delete-above [nil :up-line :up-line]}}

    ;; {:Digit0 0..:Digit9 9}
    (apply hash-map
           (mapcat identity
                   (for [n (range 10)] [(keyword (str "Digit" n)) n])))))

(def -movement-keys
  #{:Space :ArrowDown :ArrowUp :ArrowLeft :ArrowRight :Tab :Backspace})


; ---------------------------------------------------------------------
; HANDLERS ------------------------------------------------------------
; ---------------------------------------------------------------------
(defn absolute-movement-handler
  [internal-key _ state]
  (let [position (case internal-key
                   :last-line  [(dec (count (:slices @state)))]
                   :first-line [0]
                   :first-chan [nil 0 0]
                   :last-chan  [nil (dec (count (:scheme @state))) 0])]
    {:set-position position}))

(defn relative-position
  "Takes movement code (:down-line, :down-beat, etc), returns vector of
  next position."
  [internal-key [line chan attr :as active-position] state]
  (let [state-         @state
        move-vector   (get internal-values internal-key internal-key)
        [dline
         dchan        ;; this is really fuckin savage
         dattr]       (case move-vector
                        :up-jump   [(let [n (mod line 2)]
                                     (if (zero? n) -2 (- n)))
                                     0 0]
                        :down-jump [(- 2 (mod (:active-line state-) 2)) 0 0]
                        (or move-vector [0 0 0]))
        ;; since dattr is signed, dchan can just take its value when crossing over
        next-attr-    (+ attr dattr)
        dchan         (or dchan
                          (if (or (neg? next-attr-) (> next-attr- 2)) dattr 0))
        ;; we're working with one frame (32 lines) at a time and have 4 channels
        next-line     (bounded-add 31 line dline)
        next-chan     (bounded-add 3 chan dchan)
        next-attr     (if (or (and (= chan 0) (neg? dattr))
                              (and (= chan 3) (pos? dattr)))
                        (bounded-add 2 attr dattr)
                        (mod next-attr- 3))]
    [next-line next-chan next-attr]))

(defn relative-movement-handler
  [internal-key active-position state]
  (let [next-position (relative-position internal-key active-position state)]
    {:set-position next-position}))

;; refactorable
(defn edit-note-handler
  [internal-key [line chan attr :as active-position] state]
  (let [[note- pre-move post-move- :as found?] (get internal-values internal-key)
        note (if found? note- [internal-key (:octave @state)])  ; XXX bad
        post-move (or post-move- :down-line)
        pre-move-position (relative-position pre-move active-position state)
        post-move-position (relative-position post-move active-position state)]
    ; (prn (str "note- " note- "note " note))
    ;; XXX rich hickey have mercy on my soul
    {:set-attr [note (or pre-move-position active-position)]
     :set-position post-move-position
     :play-slice (when-not (or (= (get note 0) :off) (= (get note 0) :stop)) active-position)}))

;; refactorable
(defn edit-other-attr-handler
  "Only acts on numbers right now, which is... just as well."
  [internal-key active-position state]
  (let [[value- pre-move post-move-] (get-in internal-values [:oh-god internal-key])
        ;; this is intentional-------------v
        value (or value-
                  (when-let [spaghetti (get internal-values internal-key)]
                    (when-not (vector? spaghetti) spaghetti)))
        post-move (or post-move- :down-line)
        pre-move-position (relative-position pre-move active-position state)
        post-move-position (relative-position post-move active-position state)]
    ; (prn (str "post move" post-move "post-move-" post-move- "internal" internal-key))
    {:set-attr [value pre-move-position]
     :set-position post-move-position}))

(defn edit-attr-handler
  [internal-key [_ __ attr :as active-position] state]
  ; (prn attr)
  (let [handler (if (zero? attr) edit-note-handler edit-other-attr-handler)]
    (handler internal-key active-position state)))

(defn edit-octave-handler [octave _ state]
  (let [current (:octave @state)
        doctave (case octave
                  :up-octave (if (> 9 current) 1 0)
                  :down-octave (if (pos? current) -1 0)
                  0)]
    {:set-octave (+ current doctave)}))

;; XXX these handlers aren't supposed to alter state, that's what the
;; set-<name>! functions are for
(defn edit-global-handler [internal-key _ state]
  (case internal-key
    :play-pause (c/play-track state (:player @state))
    ;; TODO refactor all the position resets
    :forward-frame (do (u/check-set-frame-use state)
                       (swap! state assoc
                              :active-frame (min 31 (inc (:active-frame @state)))))
    :back-frame (do (u/check-set-frame-use state)
                    (swap! state assoc
                           :active-frame (max 0 (dec (:active-frame @state))))))
  nil)  ; returning nil skips directives

(defn edit-bpm-handler [internal-key _ state]
  (let [dbpm (internal-key
               {:tempo-up    1 :tempo-up-big    10
                :tempo-down -1 :tempo-down-big -10})]
    (prn (str "current bpm " (:bpm @state)))
    {:set-bpm (max 0 (+ dbpm (:bpm @state)))}))

(defn maybe-change-mode! [keycode state]
  (if (= :Escape keycode)
    (do (swap! state assoc :mode :normal) nil)
    (if-let [mode (and (= :normal (:mode @state))
                       (get change-mode-mappings keycode))]
      (do (swap! state assoc :mode mode) nil)
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
  (keyword (str (when (.-ctrlKey ev) "Ctrl")
                (when (.-shiftKey ev) "Shift")
                (.-code ev))))

(defn set-attr! [directive state]
  (when (:set-attr directive) ;; when-let doesn't work for destructuring
    (let [[value position] (:set-attr directive)
          [line chan attr] position
          frame (:active-frame @state)]
      (swap! state update-in [:slices frame line chan]
             #(assoc % attr value))))
  (when-let [position (:play-slice directive)]
    (c/play-slice! state (:player @state) position)))

(defn set-octave! [directive state]
  (when-let [octave (:set-octave directive)]
    (swap! state assoc :octave octave)))

(defn set-bpm! [directive state]
  (when-let [bpm (:set-bpm directive)]
    (swap! state assoc :bpm bpm)))

;; XXX these set-<name>! things could just be macros, or keyword-manipulating
;; functions
(defn set-position! [directive state]
  (let [[line chan attr] (:set-position directive)]
    (swap! state assoc
           :active-line (or line (:active-line @state))
           :active-chan (or chan (:active-chan @state))
           :active-attr (or attr (:active-attr @state)))))

(defn active-position [state]
    [(:active-line @state) (:active-chan @state) (:active-attr @state)])

;; TODO filter key inputs first? i'm a level too deep with the dispatch maps
;; TODO fuck the modes we really don't need that shit
(defn handle-keypress!
  "Translates event keycode into directives, then acts on the directives."
  [ev state]
  (when-let [keycode (-> ev
                         (prevent-movement!)
                         (translate-keycode)
                         (maybe-change-mode! state))]
    (let [active-position   (active-position state)
          dispatch-mappings (case (:mode @state)
                              :normal normal-dispatch-mappings
                              :edit insert-dispatch-mappings)
          [dispatch-key
           internal-key]    (dispatch-info keycode dispatch-mappings)
          handler           (case dispatch-key
                              :relative-movement relative-movement-handler
                              :absolute-movement absolute-movement-handler
                              :edit-attr         edit-attr-handler
                              ; edit-global can be combined into edit-octave
                              :edit-octave       edit-octave-handler
                              :edit-global       edit-global-handler
                              :edit-bpm          edit-bpm-handler
                              (constantly nil))
          directives        (handler internal-key active-position state)]
      (prn dispatch-key)
      (prn directives)
      (when (:set-attr directives)  ;; this check makes frame-jumping slightly faster
        (swap! state assoc :frame-edited true))
      (set-attr! directives state)
      (set-octave! directives state)
      (set-position! directives state)
      (set-bpm! directives state))))


(defn handle-mousedown!
  "Handles the mousedown event, which doesn't need to go through the keymappings."
  [ev state]
  (let [id (.-id (.-target ev))
       [_ literal-chan _ :as id-data] (.split id "-")
       [line chan attr :as parsed-id] (map js/parseInt id-data)]
    ; NB: If the user clicks out of the main area, 'id-data will be '"",
    ; which gets parsed to 'NaN by 'js/parseInt, so we must check for
    ; that case explicitly.
    ;; This indicates the user clicked in the main area.
    (when (and (== 3 (count parsed-id)) (every? number? parsed-id))
      (swap! state assoc
             :active-line line
             :active-chan chan
             :active-attr attr))
    (when (= "f" literal-chan)  ;; This indicates the user clicked on a page.
      (swap! state assoc
             :active-frame line)
      (u/set-frame-used?! (:active-frame @state) state))))
