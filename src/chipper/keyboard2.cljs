(ns chipper.keyboard2
  (:require [chipper.utils :as u]
            [chipper.chips :as c]
            [chipper.state :as s]
            [goog.events :as events]))

;; TODO namespace the keywords to constants

(def normal-keymap
  {:KeyJ              [:motion :down-line]
   :KeyK              [:motion :up-line]
   :KeyH              [:motion :left-line]
   :KeyL              [:motion :right-line]

   :Enter             [:motion :down-line]
   :Backspace         [:motion :up-line]
   :ArrowDown         [:motion :down-line]
   :ArrowUp           [:motion :up-line]
   :ArrowLeft         [:motion :left-line]
   :ArrowRight        [:motion :right-line]

   :KeyW              [:motion :down-measure]
   :KeyB              [:motion :up-measure]

   :BracketRight      [:motion :right-chan]
   :BracketLeft       [:motion :left-chan]

   :ShiftBracketRight [:frame  1]
   :ShiftBracketLeft  [:frame -1]

   :ShiftKeyG         [:motion :end]
   :KeyG              [:motion :beginning]
   :ShiftDigit4       [:motion :bottom]
   :Digit0            [:motion :top]

   :KeyI              [:mode :edit]
   :KeyV              [:mode :visual]

   :KeyX              [:macro [[:attr nil] [:motion :down-line]]]

   :ShiftPeriod       [:bpm :up-one]
   :ShiftComma        [:bpm :down-one]

   :Minus             [:octave :down-one]
   :Equal             [:octave :up-one]

   :Space             [:play-pause]})

(def edit-keymap
  {:KeyA      [:macro [[:attr  0] [:motion :down-line]]]
   :KeyW      [:macro [[:attr  1] [:motion :down-line]]]
   :KeyS      [:macro [[:attr  2] [:motion :down-line]]]
   :KeyE      [:macro [[:attr  3] [:motion :down-line]]]
   :KeyD      [:macro [[:attr  4] [:motion :down-line]]]
   :KeyR      [:macro [[:attr  5] [:motion :down-line]]]
   :KeyF      [:macro [[:attr  6] [:motion :down-line]]]
   :KeyT      [:macro [[:attr  7] [:motion :down-line]]]
   :KeyG      [:macro [[:attr  8] [:motion :down-line]]]
   :KeyY      [:macro [[:attr  9] [:motion :down-line]]]
   :KeyH      [:macro [[:attr 10] [:motion :down-line]]]
   :KeyU      [:macro [[:attr 11] [:motion :down-line]]]

   :KeyX      [:attr :off]
   :ShiftKeyX [:attr :stop]

   :Backspace [:macro [[:attr nil] [:motion :up-line]]]
   :Space     [:motion :down-line]

   :Minus             [:octave :down-one]
   :Equal             [:octave :up-one]

   :Escape    [:mode :normal]})

(def visual-keymap
  {:Escape [:mode :normal]})

(def mode-keymaps
  {:normal normal-keymap
   :edit   edit-keymap
   :visual visual-keymap})
