(ns chipper.core
  (:require [chipper.audio :as a]
            [chipper.keyboard :as k]
            [chipper.ui :as ui]
            [cljs.core.async :refer [<! >! take!]]
            [reagent.core :as r])
  (:require-macros [cljs.core.async.macros :refer [go-loop]]))

(enable-console-print!)

(def track
  (for [_ (range 1400)]
   [[:A nil nil nil] [:C# 4 nil nil] [:E 4 nil nil] [:G 5 nil nil]]))

(def schema [:square :square :triangle :sawtooth])

(defn on-js-reload []
  (.addEventListener
    js/window
    "keydown"
    (fn [ev]
      (prn (.-code ev))))
  (r/render-component
    [:div.container
     [ui/tracker schema track]]
    (.getElementById js/document "hack")))
