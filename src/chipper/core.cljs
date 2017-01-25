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
     :schema [:square :square :triangle :sawtooth]}))

(def context
  (r/atom
    (or (if-let [previous-state (.getItem js/localStorage "state")]
          (cljs.reader/read-string previous-state)) ;; this won't work when compiled
        {:schema (:schema @player) ;; spaghetti
         :slices (vec ;; this is a temporary dev hack... hopefully
                      (for [_ (range 256)]
                        (vec ;; this is vvv bad
                             (for [_ (:schema @player)] [nil nil nil nil]))))
         :active-line 0
         :active-chan 0
         :active-attr 0
         :octave 4
         :jump-size 2 ;; option to set
         :bpm 160 ;; option to set
         :mode :normal})))

(defonce asdf (atom {:listeners-initialized? nil}))

(when-not (:listeners-initialized? @asdf)
  (.addEventListener
    js/window
    "keydown"
    #(k/handle-keypress! % context))

  (.addEventListener
    js/window
    "mousedown"
    (fn [ev] (let [id (.-id (.-target ev))
           [line chan attr] (map js/parseInt (.split id "-"))]
       (when (every? #(number? %) [line chan attr])
         (swap! context assoc
                :active-line  line
                :active-chan  chan
                :active-attr  attr))
       (prn [id line chan attr]))))
  (swap! asdf assoc :listeners-initialized? true))

(r/render-component
  [:div.container
   ;; these derefs are a bug, actually
   [ui/main-ui (:schema @context) (:slices @context) context player]]
  (.getElementById js/document "app"))

(defn on-js-reload []
  (when-not (:listeners-initialized? @asdf)
    (.addEventListener
      js/window
      "keydown"
      #(k/handle-keypress! % context))

    (.addEventListener
      js/window
      "mousedown"
      (fn [ev] (let [id (.-id (.-target ev))
             [line chan attr] (map js/parseInt (.split id "-"))]
         (when (every? #(number? %) [line chan attr])
           (swap! context assoc
                  :active-line  line
                  :active-chan  chan
                  :active-attr  attr))
         (prn [id line chan attr]))))
    (swap! asdf assoc :listeners-initialized? true))

  (r/render-component
    [:div.container
     ;; these derefs are a bug, actually
     [ui/main-ui (:schema @context) (:slices @context) context player]]
    (.getElementById js/document "app")))
