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

(def ex-player-2-text (atom []))
(def schema [:sine :triangle])

(defn ex-player-2 [app-state track]
  [:div
   [:button
    {:on-click
     (fn []
       (let [chip   (c/create-chip! schema
                                    (:context @app-state))
             ch     (c/play-track chip track 1000)]
         (go-loop []
                  (when-let [note (<! ch)]
                    (swap! ex-player-2-text conj note)
                    (recur)))))}
    "test"]

   [:div
    (for [line @ex-player-2-text]
      ^{:key line}
      [:p {:style {:font-family "monospace"}} line])]])

(defn row
  "This is much easier SVG, not to mention easier to style and more consistent
  across browsers."
  [slice line-number]
  [:pre.slice
   ;; don't even fucking ask what happened here
   [:span.pipe-sep (as-> line-number s
                         (.toString s 16)
                         (gs/format "%2s" s)
                         (.toUpperCase s)
                         (.replace s " " "0")
                         (str " " s))]
   (for [[note octave gain effect :as attrs] slice]
     (let [note-off? (or (= :off note) (not note))
           note-name (if note-off? "-" (name note))
           octave    (if note-off? "-" octave)]
       ^{:key (gensym)} ;; XXX is this crazy
       [:span.pipe-sep
        [:span (str " " note-name "-" octave " ")]
        [:span (or gain " - ")]
        [:span (or effect " - ")]]))])

(defn tracker [schema slices]
  [:div#track
   [:pre#schema
    (apply str
           "    "
           (for [instrument schema]
             (gs/format " %-11s" (name instrument))))]
   [:div#slices
    (for [[slice line-number] (map vector slices (range))]
      ^{:key line-number} ;; uh... what
      [row slice line-number])]])

(defn controls []
  [:div#controls "asdf"])

(defn svg-shit []
  [:svg {:width 300 :height 200}
   [:rect {:width "100%"
           :height "100%"
           :fill :red}]
   [:circle {:cx 150
             :cy 100
             :r 80
             :fill :green}]
   [:text {:x 150
           :y 115
           :font-size 60
           :text-anchor :middle
           :fill :white}
    "cuck"]])
