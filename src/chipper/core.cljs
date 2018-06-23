(ns chipper.core
  (:require [chipper.keyboard :as k]
            [chipper.ui :as ui]
            [chipper.state :as s]
            [reagent.core :as r]))

(enable-console-print!)

(defonce listeners-initialized? (atom nil))

(defn register-listeners
  "Register listeners for the app. This is the 'init' code."
  [state]
  (when-not @listeners-initialized?
    (.addEventListener
      js/window
      "keydown"
      #(k/handle-keypress! % state))

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
      #(k/handle-mousedown! % state))
    (reset! listeners-initialized? true)))

(defn render-app [state]
  (r/render-component
    [ui/main-ui (s/get-player state :scheme) (:slices @state) state]
    (.getElementById js/document "app")))

(defn load-state [state]
  "Discover and load any saved state."
  (let [found-frames (s/recover-frames-or-make-new!)]
    (s/set-frames! found-frames state)
    (s/set-used-frames! found-frames state)))

(defn init-app [state]
  "Set the initial conditions and start the app."
  (register-listeners state)
  (load-state state)
  (render-app state))

(defn on-js-reload []
  "It would be nice if this were in dev.cljs automatically, somehow."
  (register-listeners))

(init-app s/state)
