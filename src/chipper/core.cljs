(ns chipper.core
  (:require [chipper.audio :as a]
            [chipper.keyboard :as k]
            [chipper.ui :as ui]
            [cljs.core.async :refer [<! >! take!]]
            [reagent.core :as r])
  (:require-macros [cljs.core.async.macros :refer [go-loop]]))

(enable-console-print!)

; (r/render-component
;     [:div.container
;      [ui/tracker schema track]]
;     (.getElementById js/document "app"))

(def context
  (r/atom
    {:schema [:square :square :triangle :sawtooth]
     :slices (for [_ (range 256)]
               [[:A nil nil nil] [:C# 4 nil nil] [:E 4 nil nil] [:G 5 nil nil]])
     :active-line 0
     :active-chan 0
     :active-attr 0
     :line-jump-size 2}))

(defonce asdf (atom {:listeners-initialized? nil}))

(defn on-js-reload []
  (when-not (:listeners-initialized? @asdf)
    (.addEventListener
      js/window
      "keydown"
      (fn [ev]
        (when (some #{(keyword (.-code ev))} (keys (:event k/movement-mappings)))
          (.preventDefault ev))
        (let [keycode (if (and (.-shiftKey ev) (= (.-code ev) "Tab"))
                        :ShiftTab
                        (keyword (.-code ev)))
              internal-code (get (:event k/movement-mappings) keycode nil)]
          (k/movement-handler internal-code context)
          (prn keycode))))

    ;; TEST
    (.addEventListener
      js/window
      "mousedown"
      (fn [ev]
        (let [id (.-id (.-target ev))
              [line chan attr] (map js/parseInt (.split id "-"))]
          (swap! context assoc
                 :active-line  line
                 :active-chan  chan
                 :active-attr  attr)
          (prn [line chan attr]))))
    (swap! asdf assoc :listeners-initialized? true))

  (r/render-component
    [:div.container
     [ui/tracker (:schema @context) (:slices @context) context]]
    (.getElementById js/document "app")))
