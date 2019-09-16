;; this needs a HUGE refactor
(ns chipper.state.chips
  (:require [chipper.constants :as const]
            [chipper.utils :as u]
            [chipper.state.audio :as a]
            [chipper.state.primitives :refer [get-player update-player set-cursor-line!]]
            [cljs.core.async :refer [<! >! close! timeout] :as async])
  (:require-macros [cljs.core.async.macros :refer [go go-loop]]))

(defn create-chip!
  "Create oscillators (and possibly an AudioContext) and return as a vector of output-connected sources"
  ([scheme] (create-chip! scheme (a/create-audio-context)))
  ([scheme audio-context]
   {:scheme scheme
    :channels (a/create-osc-channels! audio-context scheme)}))

(defn chip-for-state [state]
  (create-chip! (get-player state :scheme) (get-player state :audio-context)))

(defn chip-on! [chip]
  (doseq [channel (:channels chip)]
    (a/chan-on! channel))
  chip)

(defn chip-off! [chip]
  (doseq [channel (:channels chip)]
    (a/chan-off! channel))
  chip)

(defn set-channel-attrs!
  "Expects attrs to be a vec of [note, gain, effect]. :off turns the channel
  off, ignoring the rest of the attrs. Any nils are ignored."
  [channel attrs]
  (prn (str "attrs " attrs))
  (let [[[note octave] gain _] attrs]
    (when gain
      (a/set-gain! channel gain))
    (when note
      (if (= :off note)
        (a/chan-off! channel)
        (-> channel
            a/chan-on!
            (a/set-frequency! note octave))))))

(defn set-chip-attrs!
  "Set the attributes for all channels on the chip by consuming a directive,
  i.e., a vector of channel attrs.

  Directives are a sequence of [note, gain, effect], one element per channel."
  [chip slice]
  (doall (map set-channel-attrs!
              (:channels chip)
              slice)))
