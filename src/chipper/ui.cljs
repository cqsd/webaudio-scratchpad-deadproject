(ns chipper.ui
  (:require [goog.string :as gs]
            [goog.string.format] ;; this is needed because... closure fuckery
            [reagent.core :as r]))

;; TODO this needs to not be in this namespace
(def context (r/atom {:active-line  0
                      :active-chan  0
                      :active-attr  0}))

(defn channel [[note octave gain effect] chan-id chan-active?]
  (let [note-off?    (or (= :off note) (nil? note))
        note         (if note-off? "-" (name note)) ;(.replace (name note) "+" "#"))
        octave       (if (or note-off? (not octave)) "-" octave)
        attr-strs    [(str " " note (when (= 1 (count note)) "-") octave " ")
                      (or gain " - ")
                      (or effect " - ")]
        active-attr  (if chan-active? (:active-attr @context) -1)]
    [:span.pipe-sep
     (for [[s attr-num] (map vector attr-strs (range))
           :let [attr-id (str chan-id "-" attr-num)
                 attr-active? (and chan-active?
                                   (= attr-num active-attr))]]
       ^{:key attr-id}
       [:span {:id attr-id :class (if attr-active? :active-attr :attr)} s])]))

(defn line [slice line-id line-number line-active?]
    [:pre {:id line-id
           :class (if line-active? "slice active-line" :slice)}
     ;; -------- literally just a line number --------
     ;; don't even fucking ask what happened here
     (let [num-in-z16 (mod line-number 16)]
       [:span
        {:class (if (zero? (mod num-in-z16 4)) "pipe-sep bright-text" :pipe-sep)}
        (as-> num-in-z16 s
          (.toString s 16)
          (gs/format "%2s" s)
          (.toUpperCase s)
          (.replace s " " "0")
          (str " " s))])
     ;; ----------------------------------------------
     (let [active-chan (if line-active?
                         (:active-chan @context)
                         -1)]
       (for [[attrs chan-number] (map vector slice (range))
             :let [chan-active? (and line-active?
                                     (= chan-number active-chan))
                   chan-id (str line-number "-" chan-number)]]
         ^{:key chan-id} ;; lol
         [channel attrs chan-id chan-active?]))])

;; TODO make some SVG buttons or figure out how to be REALLY consistent
;; about the spacing, sizing, positioning of unicode buttons...
(defn add-channel-control []
  [:div#add-channel.controls
   [:span.button "+"]])

;; TEST
(.addEventListener
  js/window
  "mousedown"
  (fn [ev]
    (let [id (.-id (.-target ev))
          [line chan attr] (map js/parseInt (.split id "-"))]
      (swap! context assoc
             :active-line  line
             :active-chan  chan
             :active-attr  attr)
      (prn [line chan attr]))))

(defn main-controls []
  [:div#main-controls.controls
   [:span.button "▶"]
   [:span.button "✎"]
   (comment [:span.button "༗"])])

;; main ui
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
     (let [active-line (:active-line @context)]
       (for [[slice line-number] (map vector slices (range))
             :let [line-id (str "line-" line-number)]]
         ^{:key line-id}
         [line slice
               line-id
               line-number
               (= line-number active-line)]))]]])
