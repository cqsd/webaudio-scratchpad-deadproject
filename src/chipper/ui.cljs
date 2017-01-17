;; TODO let react handle the active element?
;; It's a hack no matter what I do, so...
(ns chipper.ui
  (:require [chipper.audio :as a]
            [chipper.chips :as c]
            [chipper.keyboard :as k]
            [chipper.notes :as n]
            [chipper.utils :as u]
            [cljs.core.async :refer [<! >! take!]]
            [goog.events :as events]
            [goog.string :as gs]
            [goog.string.format] ;; this is needed because... closure fuckery
            [reagent.core :as r])
  (:require-macros [cljs.core.async.macros :refer [go-loop]]))

(defn row
  ([slice line-number] (row slice line-number nil))
  ([slice line-number selected?]
   [:pre.slice

    ;; -------- literally just a line number --------
    ;; don't even fucking ask what happened here
    (let [shoved-into-the-ring (mod line-number 16)]
      [:span
       {:class (if (some #{shoved-into-the-ring} [0 4 8 12])
                 "pipe-sep bright-text"
                 :pipe-sep)}
       (as-> shoved-into-the-ring s
         (.toString s 16)
         (gs/format "%2s" s)
         (.toUpperCase s)
         (.replace s " " "0")
         (str " " s))])
    ;; ----------------------------------------------

    (for [[note octave gain effect :as attrs] slice]
      (let [note-off? (= :off note)
            note-name (if (or (nil? note) note-off?) "-" (name note))
            octave    (if (or (nil? octave) note-off?) "-" octave)]
        ^{:key (gensym)} ;; lol
        [:span.pipe-sep
         {:class (if selected? "pipe-sep selected" :pipe-sep)}
         [:span (str " " note-name "-" octave " ")]
         [:span (or gain " - ")]
         [:span (or effect " - ")]]))]))

(defn add-channel-control []
  [:div#hideyhack
   [:div#add-channel.controls
    [:span.button "+"]]])

(defn main-controls []
  [:div#hideyhack
   [:div#main-controls.controls
    [:span.button "▶"]
    [:span.button "✎"]
    (comment [:span.button "༗"])]])

(defn tracker [schema slices]
  [:div#tracker
   [main-controls]
   [:div#track
    [:pre#schema
     (apply str
            "    "
            (for [instrument schema]
              (gs/format " %-11s" (name instrument))))]
    [:div#slices
     (for [[slice line-number] (map vector slices (range))]
       ^{:key line-number} ;; uh... what
       [row slice line-number])]]
   [add-channel-control]])
