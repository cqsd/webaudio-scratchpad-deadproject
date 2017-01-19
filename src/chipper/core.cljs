(ns chipper.core
  (:require [chipper.keyboard :as k]
            [chipper.ui :as ui]
            [reagent.core :as r]))

(enable-console-print!)

(def schema (r/atom [:square :square :triangle :sawtooth]))

(def context
  (r/atom
    {:schema @schema
     :slices (vec ;; this is a temporary dev hack... hopefully
               (for [_ (range 100)]
                 (vec
                   (for [_ @schema] [nil nil nil nil]))))
     :active-line 0
     :active-chan 0
     :active-attr 0
     :octave 4
     :jump-size 2
     :mode :normal}))

(defonce asdf (atom {:listeners-initialized? nil}))

(defn on-js-reload []
  (when-not (:listeners-initialized? @asdf)
    (.addEventListener
      js/window
      "keydown"
      #(k/dispatcher % context))

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
     [ui/main-ui (:schema @context) (:slices @context) context]]
    (.getElementById js/document "app")))
