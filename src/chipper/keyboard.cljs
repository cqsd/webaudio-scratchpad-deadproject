(ns chipper.keyboard)

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

   ;; TODO
   ; :ShiftBracketRight [:motion :down-frame]
   ; :ShiftBracketLeft  [:motion :up-frame]

   :ShiftKeyG         [:motion :end]
   :KeyG              [:motion :beginning]
   :ShiftDigit4       [:motion :line-end]
   :Digit0            [:motion :line-beginning]

   :KeyI              [:mode :edit]
   :KeyV              [:mode :visual]

   :KeyX              [:macro [[:attr nil] [:motion :down-line]]]
   :ShiftKeyX         [:macro [[:motion :up-line] [:attr nil]]]

   :ShiftPeriod       [:bpm :up-one]
   :ShiftComma        [:bpm :down-one]

   :Minus             [:octave :down-one]
   :Equal             [:octave :up-one]

   :Space             [:play-pause]})

(def edit-keymap
  {:KeyA       [:macro [[:attr  0] [:motion :down-line]]]
   :KeyW       [:macro [[:attr  1] [:motion :down-line]]]
   :KeyS       [:macro [[:attr  2] [:motion :down-line]]]
   :KeyE       [:macro [[:attr  3] [:motion :down-line]]]
   :KeyD       [:macro [[:attr  4] [:motion :down-line]]]
   :KeyR       [:macro [[:attr  5] [:motion :down-line]]]
   :KeyF       [:macro [[:attr  6] [:motion :down-line]]]
   :KeyT       [:macro [[:attr  7] [:motion :down-line]]]
   :KeyG       [:macro [[:attr  8] [:motion :down-line]]]
   :KeyY       [:macro [[:attr  9] [:motion :down-line]]]
   :KeyH       [:macro [[:attr 10] [:motion :down-line]]]
   :KeyU       [:macro [[:attr 11] [:motion :down-line]]]
   ;; there's no clean way to make the attr handler do its own mod math, but
   ;; we'd still like to give a full octave's span without having to press
   ;; the octave key
   :KeyJ       [:macro [[:octave :up-one]
                        [:attr 0]
                        [:motion :down-line]
                        [:octave :down-one]]]

   :Digit0     [:macro [[:attr  0] [:motion :down-line]]]
   :Digit1     [:macro [[:attr  1] [:motion :down-line]]]
   :Digit2     [:macro [[:attr  2] [:motion :down-line]]]
   :Digit3     [:macro [[:attr  3] [:motion :down-line]]]
   :Digit4     [:macro [[:attr  4] [:motion :down-line]]]
   :Digit5     [:macro [[:attr  5] [:motion :down-line]]]
   :Digit6     [:macro [[:attr  6] [:motion :down-line]]]
   :Digit7     [:macro [[:attr  7] [:motion :down-line]]]
   :Digit8     [:macro [[:attr  8] [:motion :down-line]]]
   :Digit9     [:macro [[:attr  9] [:motion :down-line]]]

   :ArrowDown  [:motion :down-line]
   :ArrowUp    [:motion :up-line]
   :ArrowLeft  [:motion :left-line]
   :ArrowRight [:motion :right-line]

   :KeyX       [:macro [[:attr :off]  [:motion :down-line]]]
   :ShiftKeyX  [:macro [[:attr :stop] [:motion :down-line]]]

   :Backspace  [:macro [[:motion :up-line] [:attr nil]]]
   :Space      [:motion :down-line]

   :Minus      [:octave :down-one]
   :Equal      [:octave :up-one]

   :Escape     [:mode :normal]})

(def visual-keymap
  {:Escape [:mode :normal]})

(def mode-keymaps
  {:normal normal-keymap
   :edit   edit-keymap
   :visual visual-keymap})
