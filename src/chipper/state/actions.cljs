;; TODO it would feel more natural to pass state as the first argument to each
;; of these fns rather than the last
(ns chipper.state.actions
  (:require [chipper.keyboard :as k]
            [chipper.state.player :as player]
            [chipper.state.primitives :as p]
            [chipper.state.commands :as cmd]
            [chipper.utils :as utils]))

(defn play-pause! [_ state] ; uh oh
  (player/play-track state))

;; :/
(declare handle-property!)

(defn macro!
  [actions state]
  (doseq [[property params] actions]
    ;; this is happening here instead of pushing onto the actions queue because
    ;; all the macro actions need to happen *now*, not after the stuff already
    ;; on the queue gets done
    (handle-property! property params state)))

(def property-handlers
  {:mode p/set-mode!
   ; TODO how to automatically append lines?
   :motion p/set-relative-position!  ;; FIXME naming
   ; :absolute-position p/set-absolute-position! ;; apparently unused
   ; :frame p/set-relative-frame!
   :attr p/set-attr-at-cursor!
   :octave p/set-relative-octave!
   :bpm p/set-relative-bpm!
   :play-pause play-pause!
   :command cmd/handle-command!
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
    ;; see ui.cljs: the 3 segments of the parsed id correspond to a given attribute's
    ;; slice, channel, and attribute coordinates
    (when (and (== 3 (count parsed-id)) (every? number? parsed-id))
      (p/set-cursor-position! parsed-id state))
    (comment (when (= "f" literal-chan)  ;; This indicates the user clicked on a page.
       (swap! state assoc
              :active-frame line)
       (p/set-frame-used?! (:active-frame @state) state)))))

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

(defn handle-command-buffer!
  "input a character to the command buffer, provided it's a printable one"
  [ev state]
  (let [buf   (:command-buffer @state)
        input (.-key ev)]
  (when (utils/is-printable? input)
    (p/set-command-buffer! (str buf input) state))))

(def custom-handlers
  {:command-buffer handle-command-buffer!})

(defn handle-custom! [kind ev state]
  (when-let [handler (custom-handlers kind)]
    (handler ev state)))

(defn handle-keypress!
  "Translate the keycode, get the property and params out of the top level
  keymap, trigger the top level handler"
  [ev state]
  (when-let [keycode (-> ev -prevent-movement! -translate-keycode)]
    (let [mode              (:mode @state)
          [property params] (get-in k/mode-keymaps
                                    [mode keycode]
                                    (get-in k/mode-keymaps
                                            [mode :custom]))]
      ;; XXX this is a hack to handle command buffer, we should pass this through
      ;; a dispatch function somehow similar to handle-property!
      (if (= :command-buffer property)
        ; in the custom case, "property" is semantically more like "kind"
        ; as in, :command-buffer [handler], :info-mode [handler]
        (handle-custom! property ev state)
        (handle-property! property params state)))))
