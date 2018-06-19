(ns chipper.core
  (:require [chipper.audio :refer [create-audio-context]]
            [chipper.keyboard :as k]
            [chipper.ui :as ui]
            [chipper.utils :as u]
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
     :slices (u/recover-frames-or-make-new!)
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

(when-not @listeners-initialized?
  ;; Is there a better way to do this? not that this is bad, since this is a strictly
  ;; defined set of events, but...
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
    #(u/load-save-file! state %))

  (.addEventListener
    js/window
    "mousedown"
    #(k/handle-mousedown! % state))
  (reset! listeners-initialized? true))

(r/render-component
  [ui/main-ui (:scheme @state) (:slices @state) state player]
  (.getElementById js/document "app"))

(doseq [x (range (count (:used-frames @state)))]
  (u/set-frame-used?! x state))

;; NOTE it would be REAL nice if this were in dev.clj somehow
(defn on-js-reload []
  (when-not @listeners-initialized?
    (.addEventListener
      js/window
      "keydown"
      #(k/handle-keypress! % state))

    (.addEventListener
      js/window
      "mousedown"
      #(k/handle-mousedown! % state))
    (reset! listeners-initialized? true)))
