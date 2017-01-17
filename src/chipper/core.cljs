(ns chipper.core
  (:require [chipper.audio :as a]
            [chipper.keyboard :as k]
            [chipper.ui :as ui]
            [cljs.core.async :refer [<! >! take!]]
            [reagent.core :as r])
  (:require-macros [cljs.core.async.macros :refer [go-loop]]))

(enable-console-print!)

(def track
  (for [_ (range 100)]
   [[:A nil nil nil] [:C 4 nil nil]]))

(def schema [:square :triangle])

(defn on-js-reload []
  (r/render-component
    [:div.container
     [ui/tracker schema track]]
    (.getElementById js/document "hack")))
