;; TODO it would feel more natural to pass state as the first argument to each
;; of these fns rather than the last
(ns chipper.state.actions
  (:require [chipper.keyboard :as k]
            [chipper.state.player :as player]
            [chipper.state.primitives :as s]))

(defn play-pause! [_ state] ; uh oh
  (player/play-track state))

;; you've never seen ugly code before
(declare handle-property!)

(defn macro!
  [actions state]
  (doseq [[property params] actions]
    ;; this is happening here instead of pushing onto the actions queue because
    ;; all the macro actions need to happen *now*, not after the stuff already
    ;; on the queue gets done
    (handle-property! property params state)))

(def property-handlers
  {:mode s/set-mode!
   :motion s/set-relative-position!  ;; FIXME naming
   ; :absolute-position s/set-absolute-position! ;; apparently unused
   ; :frame s/set-relative-frame!
   :attr s/set-attr-at-cursor!
   :octave s/set-relative-octave!
   :bpm s/set-relative-bpm!
   :play-pause play-pause!
   ;; hm
   :macro macro!})

(defn handle-property!
  "Find and call the proper handler"
  [property params state]
  (when-let [handler (property-handlers property)]
    (handler params state)))

;; event handlers
(defn handle-mousedown!
  "Handles the mousedown event, which doesn't need to go through the keymappings."
  [ev state]
  (let [id (.-id (.-target ev))
        [_ literal-chan _ :as id-data] (.split id "-")
        [line chan attr :as parsed-id] (map js/parseInt id-data)]
    ; NB: If the user clicks out of the main area, 'id-data will be '"",
    ; which gets parsed to 'NaN by 'js/parseInt, so we must check for
    ; that case explicitly.
    ;; This indicates the user clicked in the main area.
    (when (and (== 3 (count parsed-id)) (every? number? parsed-id))
      (s/set-cursor-position! parsed-id state))
    (comment (when (= "f" literal-chan)  ;; This indicates the user clicked on a page.
       (swap! state assoc
              :active-frame line)
       (s/set-frame-used?! (:active-frame @state) state)))))

(def -movement-keys
  #{:Space :ArrowDown :ArrowUp :ArrowLeft :ArrowRight :Tab :Backspace})

(defn -prevent-movement! [ev]
  "Prevent movement keys (arrows, space, tab) from moving focus or scrolling."
  (when (-movement-keys (keyword (.-code ev)))
    (.preventDefault ev))
  ev)

(defn -translate-keycode [ev]
  "Takes keydown event and returns keycode as keyword; e.g., :KeyI, :Digit0.
  If SHIFT is held, prepend Shift; e.g., :ShiftKeyI.
  If CTRL is held, prepend Ctrl; e.g., :CtrlShiftKeyI."
  (keyword (str (when (.-ctrlKey ev) "Ctrl")
                (when (.-shiftKey ev) "Shift")
                ;; .-code is nil if not a keypress
                (.-code ev))))

(defn handle-keypress!
  "Translate the keycode, get the property and params out of the top level
  keymap, trigger the top level handler"
  [ev state]
  (when-let [keycode (-> ev -prevent-movement! -translate-keycode)]
    (let [mode              (:mode @state)
          [property params] (get-in k/mode-keymaps [mode keycode])]
      (handle-property! property params state))))
