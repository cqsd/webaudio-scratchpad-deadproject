;; XXX prevent scrolling on space https://stackoverflow.com/questions/18522864/disable-scroll-down-when-spacebar-is-pressed-on-firefox
;; XXX I did the mappings without thinking
;; There's a good chance there's no need at all for most of these
(ns chipper.keyboard
  (:require [cljs.core.async :refer [put! pipe chan close! timeout]]
            [goog.events :as events])
  (:require-macros [cljs.core.async.macros :refer [go-loop]]))

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
  ;; XXX should these directives be e.g. [:down :line] instead?
  ;; XXX XXX XXX this one is probably the most unnecessary for a hard mapping
  ;; probably what really needs to happen is hard map these *in the handler*
  ;; and take their values from the *dynamic config in the app*
  {:KeyJ :down-line      :ArrowDown :down-line
   :KeyK :up-line        :ArrowUp   :up-line
   :KeyH :left-column    :ArrowLeft :left-column
   :KeyL :right-column   :ArrowRight :right-column

   :BracketRight :down-measure
   :BracketLeft  :up-measure})

(def insert-mode-mappings
  {:notes note-mapping
   :volume volume-mapping
   :effects nil
   :defult nil})

(def normal-mode-mappings
  {:default movement-mappings})

(defn init-keydown-chan! []
  ;; current approach: all events go onto one channel, dispatcher takes off
  ;; and decides what to do with them
  ;; question: is there really a need for the channel at all if there's only
  ;; one dispatcher?
  (let [ch (chan)]
    (.addEventListener
      js/window
      "keydown"
      (fn [ev]
        (put! ch (.-code ev)))) ;; key code is agnostic of kb layout
    ch))
