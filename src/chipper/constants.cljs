(ns chipper.constants)

(def frame-count  32)
(def frame-length 32)
(def chan-count    4)
(def attr-count    3)
(def measure-size  4)

;; a hack, because of course
(def total-attr-positions (* chan-count attr-count))

(def motions
  ^{:doc "so this stuff can just, uh, come from some sort of config at startup"}
  {:down-line    [1  0  0]
   :up-line      [-1  0  0]
   :left-line    [0  0 -1]
   :right-line   [0  0  1]

   :right-chan   [0  0     attr-count]
   :left-chan    [0  0  (- attr-count)]

   :down-measure [measure-size  0 0]
   :up-measure   [(- measure-size) 0 0]

   ;; top/bottom stays in chan
   :bottom       [frame-length  0 0]
   :top          [(- frame-length) 0 0]
   ;; beginning/end is top left/bottom right
   :end          [frame-length  0    total-attr-positions]
   :beginning    [(- frame-length) 0 (- total-attr-positions)]})

(def -garbage
  {:up-one    1
   :down-one -1})
