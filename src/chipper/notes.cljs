(ns chipper.notes)

(def ;; Half-steps above middle C. This is easier than, e.g., indexing.
  note-steps
  {:C  0
   :C# 1  :Db 1 ;; it's just easier to do this sort of thing than it is to
   :D  2        ;; use, e.g., a translation table for accidentals
   :D# 3  :Eb 3
   :E  4
   :F  5
   :F# 6  :Gb 6
   :G  7
   :G# 8
   :A  9
   :A# 10 :Bb 10
   :B  11 :Ab 11})

(def twelfth-root-of-2 1.059) ;; only realistically need this much precision
;; (def twelfth-root-of-2 (js/Math.pow 2 (/ 1 12)))

;; Unused
; (defn- round-to-two-decs [decimal]
;   (float (/ (js/Math.round (* 100 decimal)) 100)))

(defn frequency-
  "Calculate the frequency of a note given the note name, octave number,
  and frequency of A4.

  Returns:
   - frequency (Hz)

  Params:
   - note - name of note; # for sharp, b for flat
   - octave - octave number (default 4)
   - base - base frequency of A4 (default 440)

  Reference: http://www.phy.mtu.edu/~suits/NoteFreqCalcs.html"

  ;; TODO for this use it's probably not necessary to have this variadic
  ([note]        (frequency- note 4))
  ; ([note octave] (frequency note octave 440))
  ([note octave]
   (let [freq-of-a4    440
         steps-from-c4 (+ (* (- octave 4) 12)
                          (note note-steps)
                          -9)] ;; base freq given for A4 but steps calc'd from C4
     (* freq-of-a4
        (js/Math.pow twelfth-root-of-2 steps-from-c4)))))

(def frequency (memoize frequency-))
