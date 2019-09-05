;; this needs a HUGE refactor
(ns chipper.chips
  (:require [chipper.audio :as a]
            [chipper.constants :as const]
            [chipper.utils :as u]
            [chipper.state :refer [get-player update-player set-cursor-line!]]
            [cljs.core.async :refer [<! >! close! timeout] :as async])
  (:require-macros [cljs.core.async.macros :refer [go go-loop]]))

(defn create-chip!
  "Just returns a vector of output-connected sources for now."
  ([scheme] (create-chip! scheme (a/create-audio-context)))
  ([scheme audio-context]
   {:scheme scheme
    :channels (a/create-osc-channels! audio-context scheme)}))

(defn chip-for-state [state]
  (create-chip! (get-player state :scheme) (get-player state :audio-context)))

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
  [state chip]
  (do (when-let [track (get-player state :track-chan)]
        (close! track))
      (chip-off! chip)
      (update-player state :track-chan nil)))

(defn play-track!-
  "oh my god"
  [state chip track]
  (go-loop []
    (when-let [[slice active-line] (<! track)]  ; <! is nil on closed chan
      (set-cursor-line! active-line state)
      ;; a line looks like this
      ;; [[:A 1 1] [:C ...] nil ...]
      ;; each note looks like this [:note-name dynamic unused]
      ;; map gets out the note name
      ;; now understand that some notes ([:A 1 nil]) can just be nil
      ;; filter identity just gets rid of the nils
      ;; you're left with a sequence of :A :C ...
      (let [notes (filter identity (map (comp first first) slice))]
        (if (some (partial = :stop) notes)
          (stop-track! state chip)
          (do (set-chip-attrs! chip slice)
              (recur)))))
    (stop-track! state chip)))

;; TODO XXX FIXME this can be refactored with delayed-coll-chan
;; in fact, a yank-put is basically all that's needed
;; slices are [note octave gain effect]
;; TODO change this to use setValueAtTime (or whatever it is) instead of
;; delay-based consumption because when browser is not focused, timeouts
;; only fire 1x per second at max (applies to all major browsers i think)
;; reason to do this in a go-loop is because it blocks otherwise
(defn play-track
  ([state] ; default is to play the "track" from the editor
   ;; 15000 is 60000 (ms/min) / 4 (beats in a measure)
   ;; gives duration of a single "line" (sixteenth note, sort of)
   (let [interval (/ 15000 (:bpm @state))
         ;; XXX TODO FIXME this is getting the starting line on its own and it
         ;; *shouldn't*
         active-line (:active-line @state)
         track (u/delayed-chan
                (u/enumerate (drop active-line (:slices @state)) active-line)
                interval)]
     (play-track state track)))

  ([state track] ; can pass your own track
   (if-not (get-player state :track-chan)
     (let [chip (if-let [chip- (get-player state :chip)]
                  chip-
                  (chip-for-state state))]
       (chip-off! chip)  ; XXX don't remember why this is necessary, if at all
       (update-player state :chip chip)
       (update-player state :track-chan track)
       (play-track!- state chip track))
    ;; see all these repeated stop-track! ?
    ;; this function is misnamed; it should be called play-pause-track
    ;; and it needs a refactor but so does everything else in this project
     (stop-track! state (get-player state :chip)))))

;take everything off the chan
;turn off the chip
;push shit on the chan
;turn the chip on
(defn play-slice!
  "hax to play note when you press a key"
  [state player [line _ _]]
  (when-not (get-player state :note-chip)
    (update-player state :note-chip (chip-for-state state)))
  (let [old-ch (:note-chan (:player @state))
        ch     (do
                 (update-player state :note-chan (async/chan))
                 (:note-chan (:player @state)))
        chip   (get-player state :note-chip)
        slice  (get-in @state [:slices line])]
    (chip-off! chip)
    (close! old-ch)
    (go (>! ch slice)
        (<! (timeout 280))  ; in ms
        (>! ch [[[:off nil]] nil nil])
        (close! ch))
    (go-loop []
      (when-let [slice (<! ch)]
        ;; XXX what in the fuck
        (let [notes (filter identity (map (comp first first) slice))]
          (do (set-chip-attrs! chip slice)
              (recur))))
      (chip-off! chip))))
