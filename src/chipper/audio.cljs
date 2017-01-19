;; Definitions (because this vernacular feels better to me):
;; - source - a node
;; - output - a destination
;; - channel - a plain js object: {source: source, output: output} TODO maybe rethink this
;; god this is by far the worst Clojure I have ever written
(ns chipper.audio
  (:require [chipper.notes :as n]))

(defn create-audio-context []
  (let [context (or js/window.AudioContext
                    js/window.webkitAudioContext)]
    (context.)))

(defn destination
  "This should be used everywhere you'd normally think to use .-destination,
  since in future I anticipate having the need to dispatch to different
  destinations. Using this fn means only one fn needs to be changed to
  facilitate that capability."
  [context]
  (.-destination context))

(defn chain-nodes! [node & nodes]
  "Connect nodes in order node1 node2...nodeN => node1->node2->...->nodeN

  Returns the original node."
  (loop [current node remaining nodes]
    (when (seq remaining)
      (.connect current (first remaining))
      (recur (first remaining) (rest remaining))))
  node)

(defn create-osc
  [context osc-type-]
  (let [osc-type (name osc-type-)
        osc (.createOscillator context osc-type)]
    (set! (.-type osc) osc-type)
    osc))
  ; ([context osc-type] (create-osc context osc-type :A 4))
  ; ;; TODO this second arity is probably not necessary
  ; ([context osc-type- & freq-args]
  ;  (let [osc-type (name osc-type-)
  ;        osc (.createOscillator context osc-type)
  ;        freq (if (number? (first freq-args))
  ;               (first freq-args)
  ;               (apply n/frequency freq-args)) ]
  ;    (set! (.-type osc) osc-type)
  ;    (set! (.-value (.-frequency osc)) freq)
  ;    osc)))

(defn create-gain
  ([context] (create-gain context 1))
  ([context level]
   (let [gain (.createGain context)]
     (set! (.-value (.-gain gain)) level)
     gain)))

(defn create-osc-channel! [context osc-type]
  "Create an oscillator, start it, and return a JS object containing the
  oscillator and the speaker destination."
  (let [osc (create-osc context osc-type)
        gain (create-gain context 0.1)] ;; XXX uh...
    (.start osc) ;; XXX should create-osc just start the osc before returning?
    {:source  osc
     :gain    gain
     :output  (destination context)
     :context context}))

(defn create-osc-channels! [context osc-types]
  ;; TODO change to doseq or something when sober
  (vec (map #(create-osc-channel! context %) osc-types)))

(defn chan-on! [channel]
  "Connect the channel source to its output."
  (chain-nodes! (:source channel)
                (:gain channel)
                (:output channel))
  channel)

(defn chan-off! [channel]
  "Disconnect the channel source from its output."
  (.disconnect (:source channel))
  channel)

(defn set-note! [channel & freq-args]
  (let [frequency (apply n/frequency freq-args)
        source (:source channel)]
    ;; bug in chrome; can't just set directly (causes gliss effect)
    (.setValueAtTime (.-frequency source)
                     frequency
                     (.-currentTime (:context channel)))
  channel))
