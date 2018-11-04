(ns chipper.core
  (:require [chipper.audio :refer [create-audio-context]]
            [chipper.ui :as ui]
            [chipper.state :as s]
            [chipper.actions :as a]
            [reagent.core :as r]))

(enable-console-print!)

(defn register-listeners
  "Register listeners for the app. This is the 'init' code."
  [state]
  (.addEventListener
   js/window
   "keydown"
   #(a/handle-keypress! % state))

  (.addEventListener
   js/window
   "keydown"
   #(prn (.-code %)))

  (.addEventListener
   (js/document.getElementById "file")
   "change"
   #(s/load-save-file! state %))

  (.addEventListener
   js/window
   "mousedown"
   #(a/handle-mousedown! % state))
  state)

(defn render-app [state]
  (r/render-component
   [ui/main-ui (s/get-player state :scheme) (:slices @state) state]
   (.getElementById js/document "app"))
  state)

(defn load-state [state]
  "Discover and load any saved state."
  (let [found-frames (s/recover-frames-or-make-new!)]
    (s/set-frames! found-frames state)
    (s/set-used-frames! found-frames state))
  state)

(defn init-app [state]
  "Set the initial conditions and start the app."
  (-> state
      register-listeners
      load-state
      render-app))

(init-app s/state)
