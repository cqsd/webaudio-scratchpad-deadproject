(ns chipper.core
  (:require [chipper.ui :as ui]
            [chipper.state.audio :refer [create-audio-context]]
            [chipper.state.actions :as actions]
            [chipper.state.primitives :as primitives]
            [chipper.state.player :as player]
            [chipper.state.save-load :as save-load]
            [reagent.core :as r]))

(enable-console-print!)

(defn register-listeners
  "Register listeners for the app. This is the 'init' code."
  [state]
  (.addEventListener
   js/window
   "keydown"
   #(actions/handle-keypress! % state))

  ; (.addEventListener
  ;  js/window
  ;  "keydown"
  ;  #(prn (.-code %)))

  (.addEventListener
   (js/document.getElementById "file")
   "change"
   #(save-load/load-save-file! state %))

  (.addEventListener
   js/window
   "mousedown"
   #(actions/handle-mousedown! % state))
  state)

(defn start-preview-chip-loop
  "start choochin on the note preview chan"
  [state]
  (player/initialize-preview-chip state)
  state)

(defn render-app [state]
  (r/render-component
   [ui/main-ui (primitives/get-player state :scheme) (:slices @state) state]
   (.getElementById js/document "app"))
  state)

(defn load-state [state]
  "Discover and load any saved state."
  (let [found-frames (save-load/recover-frames-or-make-new!)]
    (save-load/set-frames! found-frames state)
    (save-load/set-used-frames! found-frames state))
  (prn (str "there are " (str (count (:slices @state))) " slices"))
  state)

(defn init-app [state]
  "Set the initial conditions and start the app."
  (-> state
      register-listeners
      start-preview-chip-loop
      load-state
      render-app))

;; TODO this broke fighwheel reloads lol @neilvyas
(init-app primitives/state)
