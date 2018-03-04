(ns chipper.core
  (:require [chipper.audio :refer [create-audio-context]]
            [chipper.keyboard :as k]
            [chipper.ui :as ui]
            [reagent.core :as r]))

(enable-console-print!)

(def player
  (r/atom
    {:audio-context (create-audio-context)
     :chip nil
     :track-chan nil
     :note-chip nil  ; for playing single notes when keys are pressed
     :note-chan nil  ; sigh
     :scheme [:square :square :triangle :sawtooth]}))

(def state
  (r/atom
    {:scheme (:scheme @player) ; spaghetti; TODO find where used and point
                               ; to :player :scheme instead
     :slices (vec (repeat 32 (vec (repeat 32 (vec (repeat 4 [nil nil nil]))))))
     :active-line 0
     :active-chan 0
     :active-attr 0
     :active-frame 0
     :used-frames (vec (repeat 32 nil)) ; for indicating on the right
     :octave 4
     :bpm 100
     :mode :normal
     :player player})) ; spaghetti

(defonce listeners-initialized? (atom nil))

(when-not @listeners-initialized?
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
                :active-frame line))
       (prn [id line chan attr]))))
  (reset! listeners-initialized? true))

(r/render-component
  [ui/main-ui (:scheme @state) (:slices @state) state player]
  (.getElementById js/document "app"))

(defn on-js-reload []
  (when-not @listeners-initialized?
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
    (reset! listeners-initialized? true)))
