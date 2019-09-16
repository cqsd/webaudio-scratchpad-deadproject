;; this needs a HUGE refactor
(ns chipper.state.player
  (:require [chipper.state.audio :as a]
            [chipper.state.chips :as chips]
            [chipper.state.primitives :refer [get-player update-player set-cursor-line!]]
            [chipper.constants :as const]
            [chipper.utils :as u]
            [cljs.core.async :refer [<! >! close! timeout] :as async])
  (:require-macros [cljs.core.async.macros :refer [go go-loop]]))

(defn stop-track!
  "Close the track channel and disconnect the chip."
  [state chip]
  (do (when-let [track (get-player state :track-chan)]
        (close! track))
      (chips/chip-off! chip)
      (update-player state :track-chan nil)))

;; TODO so the plan of attack is...
;; FUCK that's gotta come later I can't focus on this shit right now
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
          (do (chips/set-chip-attrs! chip slice)
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
                  (chips/chip-for-state state))]
       (chips/chip-off! chip)  ; XXX don't remember why this is necessary, if at all
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
  (when-not (get-player state :preview-chip)
    (update-player state :preview-chip (chips/chip-for-state state)))
  (let [old-ch (:preview-chan (:player @state))
        ch     (do
                 (update-player state :preview-chan (async/chan))
                 (:preview-chan (:player @state)))
        chip   (get-player state :preview-chip)
        slice  (get-in @state [:slices line])]
    (chips/chip-off! chip)
    (close! old-ch)
    (go (>! ch slice)
        (<! (timeout 280))  ; in ms
        (>! ch [[[:off nil]] nil nil])
        (close! ch))
    (go-loop []
      (when-let [slice (<! ch)]
        ;; XXX what in the fuck
        (let [notes (filter identity (map (comp first first) slice))]
          (do (chips/set-chip-attrs! chip slice)
              (recur))))
      (chips/chip-off! chip))))

;; dev[[
(defn initialize-preview-chip
  [state]
  (let [ch   (:preview-chan (:player @state))
        chip (if-let [chip- (get-player state :preview-chip)]
               chip-
               (chips/chip-for-state state))]
    (go-loop []
      (when-let [slice (<! ch)]
        ; (prn (str "got notes " slice " on preview chan"))
        (do (chips/set-chip-attrs! chip slice)
            (recur))))))
;; ]]dev
