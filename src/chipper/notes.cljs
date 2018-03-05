(ns chipper.notes)

(defrecord Note [note octave gain effect])

;; half-steps above C
;; TODO might as well just shove em in a vector because I don't intend
;; to add any facility for enharmonics in the editor
(def note-steps
  {:C  0
   :C# 1
   :D  2
   :D# 3
   :E  4
   :F  5
   :F# 6
   :G  7
   :G# 8
   :A  9
   :A# 10
   :B  11})

;; realistically don't need this much precision
(def twelfth-root-of-2 1.059)
;; (def twelfth-root-of-2 (js/Math.pow 2 (/ 1 12)))

(defn frequency-
  {:pre [(note-steps note)]
   :doc
   "Calculate the frequency of a note given the note name, octave number,
   and frequency of A4.

   Returns:
   - frequency (Hz)

   Params:
   - note - name of note; # for sharp, b for flat
   - octave - octave number (default 4)
   - base - base frequency of A4 (default 440)

   Reference: http://www.phy.mtu.edu/~suits/NoteFreqCalcs.html"}

  [note octave]
  (let [freq-of-a4    440
        steps-from-c4 (+ (* (- octave 4) 12)
                         (note-steps note)
                         -9)] ;; base freq given for A4 but steps calc'd from C4
    (* freq-of-a4
       (js/Math.pow twelfth-root-of-2 steps-from-c4))))

(def frequency (memoize frequency-))
