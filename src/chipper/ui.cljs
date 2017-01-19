(ns chipper.ui
  (:require [chipper.chips :as c]
            [goog.string :as gs]
            [goog.string.format]
            [reagent.core :as r]))

(defn add-channel-control []
  [:div#add-channel.controls
   [:span.button "+"]])

(defn main-controls [context]
  [:div#main-controls.controls
   [:span.button {:on-click #(c/play-track context)} "▶"]
   [:span.button {:on-click #(prn (:slices @context))} "✎"]])

(defn schema-line [schema]
  [:pre#schema
   (apply str
          "     "  ;; length-5 padding
          (for [instrument schema]
            (gs/format " %-11s" (name instrument))))])

(defn modeline [& mode]
  [:pre#modeline
   [:span.pipe-sep
    (for [[stat n] (map vector mode (range))
          :let [id (str "modeline-" n)]]
      ^{:key id}
      [:span {:id id} (str stat)])]])

(defn channel
  "One line of attributes for a single channel."
  [[note octave gain effect] chan-id chan-active? context]
  (let [note        (case note
                      :off "X"
                      nil  "-"
                      (name note))
        octave      (or octave "-")
        attr-strs   [(str " " note (when (= 1 (count note)) "-") octave " ")
                     (or gain " - ")
                     (or effect " - ")]
        active-attr (if chan-active? (:active-attr @context) -1)
        mode-       (when chan-active? (:mode @context))]
    [:span.pipe-sep
     (for [[s attr-num] (map vector attr-strs (range))
           :let [attr-id (str chan-id "-" attr-num)
                 attr-active? (and chan-active?
                                   (= attr-num active-attr))
                 mode (when (and attr-active? (= mode- :insert)) mode-)]]
       ^{:key attr-id}
       [:span {:id attr-id
               :class (if attr-active?
                        (or mode :active-attr)
                        :attr)}
        s])]))

(defn line-hex-number [line-number]
  (let [num-mod (mod line-number 4096)]
    [:span
     {:class (if (zero? (mod num-mod 4)) "pipe-sep bright-text" :pipe-sep)}
     (as-> num-mod s
       (.toString s 16)
       (gs/format "%3s" s)
       (.toUpperCase s)
       (.join (.split s " ") "0")
       (str " " s))]))

(defn line
  "One line of channels"
  [slice line-id line-number line-active? context]
  [:pre {:id line-id
         :class (if line-active? "slice active-line" :slice)}
   [line-hex-number line-number]
   (let [active-chan (if line-active?
                       (:active-chan @context)
                       -1)]
     (for [[attrs chan-number] (map vector slice (range))
           :let [chan-active? (and line-active?
                                   (= chan-number active-chan))
                 chan-id (str line-number "-" chan-number)]]
       ^{:key chan-id}
       [channel attrs chan-id chan-active? context]))])

(defn main-ui
  "Main UI. Combines schema, track, controls, modeline, etc."
  [schema slices context]
  [:div#main-ui
   [main-controls context]
   [:div#tracker
    [schema-line schema]
    [:div#slices
     (let [active-line (:active-line @context)]
       (for [[slice line-number] (map vector (:slices @context) (range))
             :let [line-id (str "line-" line-number)]]
         ^{:key line-id}
         [line slice
               line-id
               line-number
               (= line-number active-line)
               context]))]
    [modeline (:mode @context)
              (keyword (str "octave" (:octave @context)))]]
   [add-channel-control]])
