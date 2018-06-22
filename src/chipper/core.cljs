(ns chipper.core
  (:require [chipper.audio :refer [create-audio-context]]
            [chipper.keyboard :as k]
            [chipper.ui :as ui]
            [chipper.state :as s]
            [reagent.core :as r]
            [cljs.core.async :refer [chan]]))

(enable-console-print!)

(def player
  (r/atom
    {:audio-context (create-audio-context)
     :chip nil
     :track-chan nil
     :note-chip nil  ; for playing single notes when keys are pressed
     :note-chan (chan 2)  ; sigh ; 18jun18 what the fuck is
     :scheme [:square :square :triangle :sawtooth]}))

(def state
  (r/atom
    {:scheme (:scheme @player) ; spaghetti; TODO find where used and point
                               ; to :player :scheme instead
     :slices (s/empty-frames)
     :active-line 0
     :active-chan 0
     :active-attr 0
     :active-frame 0
     :frame-edited nil
     :used-frames (vec (repeat 32 nil)) ; for indicating on the right
     :octave 4
     :bpm 100
     :mode :normal
     :player player})) ; spaghetti

(defonce listeners-initialized? (atom nil))

(defn register-listeners
  "Register listeners for the app. This is the 'init' code."
  []
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

(defn render-app []
  (r/render-component
    [ui/main-ui (:scheme @state) (:slices @state) state player]
    (.getElementById js/document "app")))

(defn load-state []
  "Discover and load any saved state."
  (let [found-frames (s/recover-frames-or-make-new!)]
    (s/set-frames! found-frames state)
    (s/set-used-frames! found-frames state)))

(defn init-app []
  "Set the initial conditions and start the app."
  (register-listeners)
  (load-state)
  (render-app))

(defn on-js-reload []
  "It would be nice if this were in dev.cljs automatically, somehow."
  (register-listeners))

(init-app)
