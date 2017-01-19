(ns chipper.chips
  (:require [chipper.audio :as a]
            [chipper.utils :as u]
            [cljs.core.async :refer [<! >! chan close! timeout]])
  (:require-macros [cljs.core.async.macros :refer [go-loop]]))

(def available-waves [:sine :triangle :square :sawtooth])

(defn create-chip!
  "Just returns a vector of output-connected sources for now."
  ([schema] (create-chip! schema (a/create-audio-context)))
  ([schema audio-context]
    {:schema schema
     :channels (a/create-osc-channels! audio-context schema)}))

(defn chip-off! [chip]
  (doseq [channel (:channels chip)]
    (a/chan-off! channel))
  chip)

;; TODO name this better
(defn set-channel-attrs!
  "Expects attrs to be a vec of [note, gain, effect]. :off turns the channel
  off, ignoring the rest of the attrs. Any nils are ignored."
  [channel attrs]
  (let [[note octave gain effect] attrs]
    (when note
      (if (= :off note)
        (a/chan-off! channel)
        (-> channel
            a/chan-on!
            (a/set-note! note octave))))))

;; TODO name this better
(defn set-chip-attrs!
  "Set the attributes for all channels on the chip by consuming a directive,
  i.e., a vector of channel attrs.

  Directives are a sequence of [note, gain, effect], one element per channel."
  [chip slice]
  (doall (map set-channel-attrs!
              (:channels chip)
              slice)))

;; TODO XXX FIXME this can be refactored with delayed-coll-chan
;; in fact, a yank-put is basically all that's needed
;; slices are [note octave gain effect]
;; TODO change this to use setValueAtTime (or whatever it is) instead of
;; delay-based consumption because when browser is not focused, timeouts
;; only fire 1x per second at max (applies to all major browsers i think)
(defn play-track
  "Repeatedly consume slices (at a set interval) until the sequence of slices
  is empty."
  [context] ;; TODO XXX FIXME start here next time
  ;; TODO need to clear all the chip attributes before playback
  (prn "here we are")
  (let [state @context
        interval (/ 15000 (:bpm state))
        chip- (:chip state)
        chip (if chip- chip- (create-chip! (:schema state) (:audio-context state)))
        track (u/delayed-coll-chan (:slices state) interval)]
    (chip-off! chip)
    (swap! context assoc :chip chip)
    (go-loop []
      (when-let [slice (<! track)]
        (print (apply str (map first slice))) ;; TODO derete
        (set-chip-attrs! chip slice)
        (recur))
      (chip-off! chip))))
