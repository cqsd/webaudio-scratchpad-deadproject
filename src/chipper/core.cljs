(ns chipper.core
  (:require [chipper.keyboard :as k]
            [chipper.ui :as ui]
            [chipper.state :as s]
            [reagent.core :as r]))

(enable-console-print!)

(defonce listeners-initialized? (atom nil))

(defn register-listeners
  "Register listeners for the app. This is the 'init' code."
  []
  (when-not @listeners-initialized?
    (.addEventListener
      js/window
      "keydown"
      #(k/handle-keypress! % s/state))

    (.addEventListener
      js/window
      "keydown"
      #(prn (.-code %)))

    (.addEventListener
      (js/document.getElementById "file")
      "change"
      #(s/load-save-file! s/state %))

    (.addEventListener
      js/window
      "mousedown"
      #(k/handle-mousedown! % s/state))
    (reset! listeners-initialized? true)))

(defn render-app []
  (r/render-component
    [ui/main-ui (s/get-player s/state :scheme) (:slices @s/state) s/state]
    (.getElementById js/document "app")))

(defn load-state []
  "Discover and load any saved state."
  (let [found-frames (s/recover-frames-or-make-new!)]
    (s/set-frames! found-frames s/state)
    (s/set-used-frames! found-frames s/state)))

(defn init-app []
  "Set the initial conditions and start the app."
  (register-listeners)
  (load-state)
  (render-app))

(defn on-js-reload []
  "It would be nice if this were in dev.cljs automatically, somehow."
  (register-listeners))

(init-app)
