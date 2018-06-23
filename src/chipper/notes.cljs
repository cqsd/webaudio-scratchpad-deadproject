(ns chipper.notes)

; note - number of semitones above C
; octave - octave number
(defrecord Note [note octave gain effect])

;; Display notes.
(def -name-rel-c
  {0 :C
   1 :C#
   2 :D
   3 :D#
   4 :E
   5 :F
   6 :F#
   7 :G
   8 :G#
   9 :A
   10 :A#
   11 :B})

(def -semitone-rel-c (zipmap (vals -name-rel-c) (keys -name-rel-c)))

(defn name-rel
  [root-name semitone]
  (let [root-semitone (root-name -semitone-rel-c)
        adjusted-semitone (mod (+ root-semitone semitone) 11)]
    (get -name-rel-c adjusted-semitone)))


;; realistically don't need this much precision
(def twelfth-root-of-2 1.059)
;; (def twelfth-root-of-2 (js/Math.pow 2 (/ 1 12)))

(defn frequency-
  {:doc
   "Calculate the frequency of a note given the note name, octave number,
   and frequency of A4.

   Returns:
   - frequency (Hz)

   Params:
   - note - semitones relative to C
   - octave - octave number (default 4)
   - base - base frequency of A4 (default 440)

   Reference: http://www.phy.mtu.edu/~suits/NoteFreqCalcs.html"}

  [note octave]
  (let [freq-of-a4    440
        steps-from-c4 (+ (* (- octave 4) 12)
                         note
                         -9)] ;; base freq given for A4 but steps calc'd from C4
    (* freq-of-a4
       (js/Math.pow twelfth-root-of-2 steps-from-c4))))

(def frequency (memoize frequency-))
