;; Definitions OK here's a TODO I don't think I actually use these arbitrary
;; naming rules, so do a quick check for where I do and undo it to avoid
;; confusion with the WebAudio API
;; - source - a node
;; - output - a destination
;; - channel - a plain js object: {source: source, output: output}
(ns chipper.audio
  (:require [chipper.notes :as n]
            [chipper.utils :as u]))

(def default-gain-level 0.1)

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
  "Available types are sine, square, triangle, sawtooth. Prefer passing type as
  a keyword (e.g. :sine) for style.

  https://developer.mozilla.org/en-US/docs/Web/API/OscillatorNode/type"
  [context osc-type-]
  (let [osc-type (name osc-type-)
        osc (.createOscillator context osc-type)]
    (set! (.-type osc) osc-type)
    osc))

(defn create-gain
  "Gain level should be in [0,1]."
  [context level]
  (let [gain (.createGain context)]
    (set! (.-value (.-gain gain)) level)
    gain))

;; the ! naming is inconsistent in this namespace; the standalone create-* fns
;; actually change state but don't have the bang
(defn create-osc-channel! [context osc-type]
  "Create an oscillator, start it, and return a JS object containing the
  oscillator and the speaker destination."
  (let [osc (create-osc context osc-type)
        gain (create-gain context default-gain-level)]
    (.start osc)
    ;; I know what it says in the docstring but this is not a raw JS object and
    ;; I need to check that no code anywhere expects it to be. I mean, if it
    ;; did, shit would break, so I don't expect so. It's also deeply unaesthetic
    ;; to have a random JS object here but I think it maybe happens elsewhere
    {:source  osc
     :gain    gain
     :output  (destination context)
     :context context}))

(defn create-osc-channels! [context osc-types]
  ;; TODO find out if mapv works in cljs because I can't be bothered to wait
  ;; for a repl to start right now
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

(defn set-frequency! [channel & freq-args]
  (let [frequency (apply n/frequency freq-args)
        source (:source channel)]
    ;; bug in chrome; can't just set directly (causes gliss effect)
    (.setValueAtTime (.-frequency source)
                     frequency
                     (.-currentTime (:context channel)))
  channel))

;; TODO I don't know what gain-digit is, and I'd like to find out
(defn set-gain! [channel gain-digit]
  (let [normalized (u/normalize-digit gain-digit)
        gain (:gain channel)]
    ;; (println gain)
    (.setValueAtTime (.-gain gain)
                     normalized
                     (.-currentTime (:context channel)))))
