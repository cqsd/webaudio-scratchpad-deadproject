;; this needs a HUGE refactor
(ns chipper.chips
  (:require [chipper.audio :as a]
            [chipper.utils :as u]
            [cljs.core.async :refer [<! >! close! timeout] :as async])
  (:require-macros [cljs.core.async.macros :refer [go go-loop]]))

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
  [state player chip]
  (do (when-let [track (:track-chan @player)]
        (close! track))
      (chip-off! chip)
      (swap! player assoc :track-chan nil)))

(defn play-track!-
  "oh my god"
  [state player chip track]
  (go-loop []
           (when-let [[frame line slice] (<! track)]  ; <! is nil on closed chan
             (prn (str "slice id " frame " " line))
             ; XXX this swap is the only reason to pass state in here;
             ; find a way to remove it (e.g. by using core async the *intended*
             ; way
             (swap! state assoc
                    :active-frame frame
                    :active-line line)
             (let [notes (filter identity (map (comp first first) slice))]
               (if (some (partial = :stop) notes)
                 (stop-track! state player chip)
                 (do (set-chip-attrs! chip slice)
                     (recur)))))
           (stop-track! state player chip)))

;; TODO XXX FIXME this can be refactored with delayed-coll-chan
;; in fact, a yank-put is basically all that's needed
;; slices are [note octave gain effect]
;; TODO change this to use setValueAtTime (or whatever it is) instead of
;; delay-based consumption because when browser is not focused, timeouts
;; only fire 1x per second at max (applies to all major browsers i think)
;; reason to do this in a go-loop is because it blocks otherwise
(defn play-track
  ([state player] ; default is to play the "track" from the editor
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

  ([state player track] ; can pass your own track
  (if-not (:track-chan @player)
    (let [chip (if-let [chip- (:chip @player)]
                 chip-
                 (create-chip! (:scheme @state) (:audio-context @player)))]
      (chip-off! chip)  ; XXX don't remember why this is necessary, if at all
      (swap! player assoc :chip chip :track-chan track)
      (play-track!- state player chip track))
    ;; see all these repeated stop-track! ?
    ;; this function is misnamed; it should be called play-pause-track
    ;; and it needs a refactor but so does everything else in this project
    (stop-track! state player (:chip @player)))))

;take everything off the chan
;turn off the chip
;push shit on the chan
;turn the chip on
(defn play-slice!
  "more delicious spaghetti for playing single line when you enter a note"
  [state player [line & _ :as active-position]]
  (when-not (:note-chip @player)
    (swap! player assoc
           :note-chip (create-chip! (:scheme @state) (:audio-context @player))))
  (let [ch-    (:note-chan @player)
        ch     (:note-chan (swap! player assoc :note-chan (async/chan)))
        chip   (:note-chip @player)
        slice  (get-in @state [:slices (:active-frame @state) line])
        track- [[0 0 slice] [0 0 [[[:off nil]] nil nil]]]]
    (chip-off! chip)
    (close! ch-)
    (go (>! ch [0 0 slice])
        (<! (timeout 280))  ; in ms
        (>! ch [0 0 [[[:off nil]] nil nil]])
        (close! ch))
    (go-loop []
             (when-let [[frame line slice] (<! ch)]
               (let [notes (filter identity (map (comp first first) slice))]
                 (do (set-chip-attrs! chip slice)
                     (recur))))
             (chip-off! chip))))
