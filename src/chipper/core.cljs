(ns chipper.core
  (:require [chipper.audio :refer [create-audio-context]]
            [chipper.keyboard :as k]
            [chipper.ui :as ui]
            [reagent.core :as r]))

(enable-console-print!)

(def player
  (r/atom
    {:audio-context (create-audio-context)
     ;; I forgot, but I think :chip is a vec of oscillators?
     ;; If it exists, it's used. If not, it's created on play
     :chip nil
     :scheme [:square :square :triangle :sawtooth]}))

(def state
  (r/atom
    {:scheme (:scheme @player) ;; spaghetti
     :slices (vec (repeat 32 (vec (repeat 32 (vec (repeat 4 [nil nil nil]))))))
     :active-line 0
     :active-chan 0
     :active-attr 0
     :active-frame 0
     :used-frames (vec (repeat 32 nil))
     :octave 4
     :jump-size 1 ;; add user option
     :bpm 100     ;; add user option
     :mode :normal
     :player player}))

(defonce asdf (atom {:listeners-initialized? nil}))

(when-not (:listeners-initialized? @asdf)
  ;; Is there a better way to do this? not that this is bad, since this is a strictly
  ;; defined set of events, but...
  (.addEventListener
    js/window
    "keydown"
    #(k/handle-keypress! % state))

  (.addEventListener
    js/window
    "keydown"
    #(prn (.-code %)))

  (.addEventListener
    js/window
    "mousedown"
    (fn [ev]
      (let [id (.-id (.-target ev))
           [line- chan- attr- :as id-data] (.split id "-")
           [line chan attr] (map js/parseInt id-data)]
       (when (every? #(number? %) [line chan attr])
         (swap! state assoc
                :active-line  line
                :active-chan  chan
                :active-attr  attr))
       (when (= "f" chan-)
         (swap! state assoc-in
                [:used-frames (:active-frame @state)]
                (some identity
                      (sequence (comp cat cat)
                                ((:slices @state) (:active-frame @state)))))
         (swap! state assoc
                :active-frame line
                :active-line 0
                :active-chan 0
                :active-attr 0))
       (prn [id line chan attr]))))
  (swap! asdf assoc :listeners-initialized? true))

(r/render-component
  [ui/main-ui (:scheme @state) (:slices @state) state player]
  (.getElementById js/document "app"))

(defn on-js-reload []
  (when-not (:listeners-initialized? @asdf)
    (.addEventListener
      js/window
      "keydown"
      #(k/handle-keypress! % state))

    (.addEventListener
      js/window
      "mousedown"
      (fn [ev] (let [id (.-id (.-target ev))
                     [line chan attr] (map js/parseInt (.split id "-"))]
                 (when (every? #(number? %) [line chan attr])
                   (swap! state assoc
                          :active-line  line
                          :active-chan  chan
                          :active-attr  attr))
                 (prn [id line chan attr]))))
    (swap! asdf assoc :listeners-initialized? true)))
