(ns chipper.constants)

;; there's really no point calling it a frame, might as well just
;; call it "arbitrary-jump-size"
(def frame-count  16)
(def frame-length 32)
(def max-line-count (* frame-count frame-length))
(def last-position (dec max-line-count))
(def chan-count    4)
(def attr-count    3)
(def measure-size  4)
(def marker-distance (* 2 measure-size)) ; idk, used for the bright flags on the line numbers

;; cf vim's scrolloff=
(def scrolloff 2)
;; number of lines of track to display (we'll make this dynamic with resizes
;; eventually)
(def view-size  32)

;; a hack, because of course
(def total-attr-positions (* chan-count attr-count))

;; format is [dline dchan dattr]
(def motions
  ^{:doc "so this stuff can just, uh, come from some sort of config at startup"}
  {:down-line      [1 0 0]
   :up-line        [-1 0 0]
   :left-line      [0 0 -1]
   :right-line     [0 0 1]

   ;; I'm SO CONFUSED at how this works, I have no fucking clue
   :right-chan     [0 0 attr-count]
   :left-chan      [0 0 (- attr-count)]

   :down-measure   [measure-size 0 0]
   :up-measure     [(- measure-size) 0 0]

   :down-frame     [frame-length 0 0]
   :up-frame       [(- frame-length) 0 0]

   ;; see above confusion; why is it a dattr?
   :line-end       [0 0 (* attr-count chan-count)]
   :line-beginning [0 1 (- (* attr-count chan-count))]

   :end            [js/Infinity 0 0]
   :beginning      [(- js/Infinity) 0 0]})

(def -garbage
  {:up-one    1
   :down-one -1})
