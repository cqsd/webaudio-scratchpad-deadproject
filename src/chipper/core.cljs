(ns chipper.core
  (:require [chipper.audio :as a]
            [chipper.keyboard :as k]
            [chipper.ui :as ui]
            [cljs.core.async :refer [<! >! take!]]
            [reagent.core :as r])
  (:require-macros [cljs.core.async.macros :refer [go-loop]]))

(enable-console-print!)

(def app-state
  (r/atom
    (let [context (a/create-audio-context)]
      {:context  context
       :sources {:square   (a/create-osc context "square" :A)
                 :triangle (a/create-osc context "triangle" :C 5)
                 :sawtooth (a/create-osc context "sawtooth" :A 3)}})))

; (def track
;   [[[:A 4 nil nil] [:C 4 nil nil]]
;    [[:B 4 nil nil] [:D 4 nil nil]]
;    [[:C 5 nil nil] [:E 4 nil nil]]
;    [[:D 5 nil nil] [:F 4 nil nil]]
;    [[:E 5 nil nil] [:G 4 nil nil]]
;    [[:F 5 nil nil] [:A 4 nil nil]]
;    [[:F 5 nil nil] [:B 4 nil nil]]
;    [[:E 5 nil nil] [:C 5 nil nil]]])

(def track
  (for [_ (range 100)]
   [[:A nil nil nil] [:C 4 nil nil]]))

(def event-chan (r/atom (k/init-keydown-chan!)))

(defn on-js-reload []
  (r/render-component
    [:div.container
     [ui/tracker ui/schema track]
     ;[ui/ex-player-2 app-state track]
     ]
    (.getElementById js/document "hack"))

  (go-loop [x (<! @event-chan)]
    (prn (keyword x))
    (recur (<! @event-chan))))
