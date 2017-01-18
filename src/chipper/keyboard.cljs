;; XXX prevent scrolling on space https://stackoverflow.com/questions/18522864/disable-scroll-down-when-spacebar-is-pressed-on-firefox
;; XXX I did the mappings without thinking
;; There's a good chance there's no need at all for most of these
(ns chipper.keyboard
  (:require [cljs.core.async :refer [put! pipe chan close! timeout]]
            [goog.events :as events])
  (:require-macros [cljs.core.async.macros :refer [go-loop]]))

; ---------------------------------------------------------------------
; MAPPINGS ------------------------------------------------------------
; ---------------------------------------------------------------------

(def note-mapping
  (merge                   ; idk how to process shift using keycodes so
    {:KeyA :C   :KeyW :C#  ; :KeyA :C+
     :KeyE :D   :KeyR :D#  ; :KeyE :D+
     :KeyD :E
     :KeyF :F   :KeyT :F#  ; :KeyF :F+
     :KeyG :G   :KeyY :G#  ; :KeyG :G+
     :KeyH :A   :KeyI :A#  ; :KeyH :A+
     :KeyJ :B

     :BracketRight :upoctave  ; XXX this is very suspect
     :BracketLeft  :downoctave}

    ;; {:Digit1 :octave1..:Digit0 :octave0}
    (apply merge ; is this even necessary? maybe just write into the handler
           (for [n (range 0 10)]
             {(keyword (str "Digit" n))
              (keyword (str "octave" n))}))))

(def volume-mapping ; is this even necessary? maybe just write into the handler
  ;; {:Digit1 1...:Digit0 0}
  (apply merge
         (for [n (range 0 10)]
           {(keyword (str "Digit" n)) n})))

(def movement-mappings
  ;; TODO jump keys (g, 0, etc)
  {:event  ;; event mappings take event data -> internal rep
   {:KeyJ :down-line      :ArrowDown  :down-line
    :KeyK :up-line        :ArrowUp    :up-line
    :KeyH :left-attr    :ArrowLeft  :left-attr
    :KeyL :right-attr   :ArrowRight :right-attr

    :Tab      :right-chan   :KeyW :right-chan
    :ShiftTab :left-chan    :KeyB :left-chan

    ;; TODO
    ; :Equal :?
    ; :Minus :?
    :BracketRight :down-measure
    :BracketLeft  :up-measure}

   :internal  ;; internal mappings take internal -> whatever is needed for use?
   {:down-line    [1 nil 0] :up-line    [-1 nil 0]
    :right-attr   [0 nil 1] :left-attr  [0 nil -1]
    :right-chan   [0 1 0]   :left-chan  [0 -1 0]
    :down-measure [4 nil 0] :up-measure [-4 nil 0]}})

(def insert-mode-mappings
  {:notes note-mapping
   :volume volume-mapping
   :effects nil
   :defult nil})

(def normal-mode-mappings
  {:default movement-mappings})

; ---------------------------------------------------------------------
; HANDLERS ------------------------------------------------------------
; ---------------------------------------------------------------------

;; XXX keycode needs to be a keyword...
(defn movement-handler [internal-code context]
  ;; abusing the let macro...
  (let [state         @context ;; I foresee a race condition
        active-line   (:active-line state)  ;; there's gotta be a better way
        active-chan   (:active-chan state)  ;; to make these selections
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

; (prn {:active-attr (:active-attr @context)
;           :active-chan (:active-chan @context)
;           :active-line (:active-line @context)})

;; TODO this needs to go somewhere else (should the chan also handle mousdown?)
(defn init-keycode-chan! []
  (let [ch (chan)]
    (.addEventListener ;; XXX is this really the best we can do
      js/window
      "keydown"
      (fn [ev]
        (put! ch (keyword (.-code ev))))) ;; key code is agnostic of kb layout
    ch))
