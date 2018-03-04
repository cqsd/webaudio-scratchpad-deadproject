;; this needs a HUGE refactor
(ns chipper.chips
  (:require [chipper.audio :as a]
            [chipper.utils :as u]
            [cljs.core.async :refer [<! >! chan close! timeout]])
  (:require-macros [cljs.core.async.macros :refer [go-loop]]))

(def available-waves [:sine :triangle :square :sawtooth])

(defn create-chip!
  "Just returns a vector of output-connected sources for now."
  ([scheme] (create-chip! scheme (a/create-audio-context)))
  ([scheme audio-context]
    {:scheme scheme
     :channels (a/create-osc-channels! audio-context scheme)}))

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

(defn stop-track!
  "Close the track channel and disconnect the chip."
  [state player]
  (do (when-let [track (:track-chan @player)]
        (close! track))
      (chip-off! (:chip @player))
      (swap! player assoc :track-chan nil)))

;; TODO XXX FIXME this can be refactored with delayed-coll-chan
;; in fact, a yank-put is basically all that's needed
;; slices are [note octave gain effect]
;; TODO change this to use setValueAtTime (or whatever it is) instead of
;; delay-based consumption because when browser is not focused, timeouts
;; only fire 1x per second at max (applies to all major browsers i think)
;; reason to do this in a go-loop is because it blocks otherwise
(defn play-track
  "Repeatedly consume slices (at a set interval) until the sequence of slices
  is empty."
  ([state player]
   (let [interval (/ 15000 (:bpm @state))
         ;; ok this enumerate is the hack for changing frame numbers as we go
         ;; god damn. We do a drop to start at the current frame
         track (u/delayed-chan
                 (apply concat ; generate [[frame line slice]...]
                        (for [[frame i] (u/enumerate (drop (:active-frame @state)
                                                           (:slices @state))
                                                     (:active-frame @state))]
                          (for [[slice j] (u/enumerate frame)] [i j slice])))
                 interval)]
     (play-track state player track)))

  ([state player track]
  (if-not (:track-chan @player)
    (let [state- @state
          player- @player
          chip (if-let [chip- (:chip player-)]
                 chip-
                 (create-chip! (:scheme state-) (:audio-context player-)))]
      (chip-off! chip)
      (swap! player assoc :chip chip :track-chan track)
      (go-loop []
               (when-let [[frame line slice] (<! track)]  ; <! is nil on closed chan
                 (prn (str "slice id " frame " " line))
                 (swap! state assoc
                        :active-frame frame
                        :active-line line)
                 (let [notes (filter identity (map (comp first first) slice))]
                   (if (some (partial = :stop) notes)
                     (stop-track! state player)
                     (do (set-chip-attrs! chip slice)
                         (recur)))))
               (stop-track! state player)))
    ;; see all these repeated stop-track! ?
    ;; this function is misnamed; it should be called play-pause-track
    ;; and it needs a refactor but so does everything else in this project
    (stop-track! state player))))
