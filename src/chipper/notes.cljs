(ns chipper.notes)

; note - number of semitones above C
; octave - octave number
(defrecord Note [note octave gain effect])

;; Display notes.
(def -name-rel-c
  {0  :C
   1  :C#
   2  :D
   3  :D#
   4  :E
   5  :F
   6  :F#
   7  :G
   8  :G#
   9  :A
   10 :A#
   11 :B})

;; https://pdfs.semanticscholar.org/71a0/7f546a24b4ea7e866d9f9a696eb5f32fb662.pdf
(def werckmeister-p (/ 531441 524288))

(def -custom-temperament-ratios
  ; pythagorean atm
  {:pythagorean [1
                 (/ 256 243)
                 (/ 9 8)
                 (/ 32 27)
                 (/ 81 64)
                 (/ 4 3)
                 (/ 729 512)
                 (/ 3 2)
                 (/ 128 81)
                 (/ 27 16)
                 (/ 16 9)
                 (/ 243 128)]
   :werckmeister [1
                  (/ 256 243)
                  (/ 9 8 (js/Math.pow werckmeister-p 0.5))
                  (/ 32 27)
                  (/ 81 64 (js/Math.pow werckmeister-p 0.75))
                  (/ 4 3)
                  (/ 1024 729)
                  (/ 3 2 (js/Math.pow werckmeister-p 0.25))
                  (/ 128 81)
                  (/ 27 16 (js/Math.pow werckmeister-p 0.75))
                  (/ 16 9)
                  (/ 243 128 (js/Math.pow werckmeister-p 0.75))]})

(def -semitone-rel-c (zipmap (vals -name-rel-c) (keys -name-rel-c)))

(defn name-rel
  [root-name semitone]
  (let [root-semitone (root-name -semitone-rel-c)
        adjusted-semitone (mod (+ root-semitone semitone) 12)]
    (get -name-rel-c adjusted-semitone)))

; (def twelfth-root-of-2 1.059)
(def twelfth-root-of-2 (js/Math.pow 2 (/ 1 12)))

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

(defn frequencies-for-fundamental-
  "note is semitones from C, system is one of the tuning systems defined above"
  [note system]
  (let [freq-of-fundamental (frequency- note 4)] ; 4 is octave number (e.g. C4)
    (mapv #(* freq-of-fundamental %)
          (system -custom-temperament-ratios))))

(def frequencies-for-fundamental (memoize frequencies-for-fundamental-))

(defn frequency-custom-temperament-
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
  (let [fundamental       4 ; see -name-rel-c
        base-octave-freqs (frequencies-for-fundamental fundamental :werckmeister)
        steps-from-tonic  (- note fundamental)
        octave-multiplier (js/Math.pow 2
                                       (- octave 4 (if (neg? steps-from-tonic) 1 0)))
        base-freq         (base-octave-freqs (mod steps-from-tonic 12))
        freq              (* base-freq octave-multiplier)]
    (prn (str
           "note " note
           ; " fundamental " fundamental
           ; " fund-freq " (base-octave-freqs 0)
           ; " steps " steps-from-tonic
           " octave " octave
           " multiplier " octave-multiplier
           " freq  " freq))
    freq))

(def frequency (memoize frequency-))
; (def frequency (memoize frequency-custom-temperament-))
