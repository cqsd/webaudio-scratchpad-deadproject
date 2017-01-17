(ns chipper.chips
  (:require [chipper.audio :as a]
            [chipper.utils :as u]
            [cljs.core.async :refer [<! >! chan close! timeout]])
  (:require-macros [cljs.core.async.macros :refer [go-loop]]))

(def available-waves [:sine :triangle :square :sawtooth])

(defn create-chip!
  "Just returns a vector of output-connected sources for now."
  ([schema] (create-chip! schema (a/create-audio-context)))
  ([schema context]
    {:schema schema
     :channels (a/create-osc-channels! context schema)}))

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
(defn play-track
  "Repeatedly consume slices (at a set interval) until the sequence of slices
  is empty."
  [chip track interval] ;; TODO XXX FIXME start here next time
  (let [slices (u/delayed-coll-chan track interval)
        ch (chan)]
    (go-loop []
      (when-let [slice (<! slices)]
        ;; (print (apply str (map first note))) ;; TODO derete
        (>! ch (str slice)) ;; TODO derete
        (set-chip-attrs! chip slice)
        (recur))
     (close! ch)
     (chip-off! chip))
    ch))
