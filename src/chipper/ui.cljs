(ns chipper.ui
  (:require [chipper.chips :as c]
            [chipper.constants :as const]
            [chipper.state :as s]
            [chipper.notes :as n]
            [chipper.utils :as u]
            [goog.string :as gs]
            [goog.string.format]
            [reagent.core :as r]))

;; TODO FIXME: everything is relative to :C right now.

(defn note-name [semitone] (name (n/name-rel :C semitone)))

(defn scheme-line [scheme]
  [:pre#scheme
   (apply str
          "    "  ;; length-3 padding...
          (for [instrument scheme]
            (gs/format " %-11s" (name instrument))))])

(defn edit-mode [mode]
  [:span {:class (when (= :edit mode) "bright-text")}
   (str " " (name mode))])

(defn modeline [left state]
  [:pre#modeline.flex-spread
   [:span#modeline-left [edit-mode (:mode @state)] left]
   [:span#modeline-right
    [:label {:class :button :for :file} "load"]
    " "
    [:span {:id :save
            :class (str "button"
                        (when (:frame-edited @state) " bright-text"))
            ;; TODO FIXME don't call save-state directly, make it an action.
            :on-click #(s/save! state)}
     "save"]
    " "
    [:span {:id :play
            :class (str "button"
                        (when (:track-chan (:player @state))
                          " bright-text"))
            :on-click #(c/play-track state)}
     (if (:track-chan (:player @state)) "pause" "play")] " "]])

(defn channel
  "One line of attributes for a single channel."
  [[note- gain effect] chan-id chan-active? state]
  (let [[note-- octave] note-
        note        (case note--
                      :off "X"
                      :stop "S"
                      nil  "-"
                      (note-name note--))
        octave      (or octave "-")
        attr-strs   [(str " " note (when (= 1 (count note)) "-") octave " ")
                     (if gain   (str " " gain " ")   " - ")
                     (if effect (str " " effect " ") " - ")]
        active-attr (if chan-active? (:active-attr @state) -1)
        mode-       (when chan-active? (:mode @state))]
    [:span.ps
     (for [[s attr-num] (map vector attr-strs (range))
           :let [attr-id (str chan-id "-" attr-num)
                 attr-active? (and chan-active?
                                   (= attr-num active-attr))
                 ;; spaghetti; changing this shit is gonna suck
                 mode (when (and attr-active? (= mode- :edit)) mode-)]]
       ^{:key attr-id}
       [:span {:id attr-id
               :class (str (name (if attr-active?
                                   (or mode :active-attr)
                                   :attr))
                           (when (= note-- :off)  " bright-text")
                           (when (= note-- :stop) " stopline"))}
        s])])) ; <-- it's part of the span

;; XXX this is horrible
(comment (defn number->hex [n]
           (as-> n s
             (.toString s 16)
             (gs/format "%2s" s)
             (.toUpperCase s)
             (.join (.split s " ") "0"))))

(defn number->hex [n]
  ;; dev[[ make this mod 16 instead of "raw" (too many digits); we need to add
  ;; some sort of indication of which frame you're on as well as frame boundaries ]]dev
  (as-> n s
    (mod s 32)
    (.toString s 16)
    ; (.toString s)
    (.toUpperCase s)
    (str (when-not (even? (count s)) "0") s)))

(defn line-hex [line-number]
  (let [hex (number->hex line-number)]
    [:span [:span
            {:class (when (zero? (mod line-number const/measure-size))
                      :beat-marker)
             ; (if (zero? (mod line-number const/marker-distance))
             ;   ; TODO either do this or actually change the text
             ;   ; to indicate that it's a new "measure"
             ;   :measure-marker
             ;   (when (zero? (mod line-number const/measure-size))
             ;     :beat-marker))
             }
            (str " " hex " ")] ""]))

;; XXX this needs to 1: be its own column (flex div) on the right instead
;; of part of the line; it's slowing redraws way too much
(comment (defn frame-hex [line-number window-index state]
           "The high-level view on the right side of the editor"
           (let [active-frame (quot line-number const/frame-length)
                 frame-number (+ window-index active-frame)
                 hex (number->hex frame-number)]
             [:span.attr
              {:id (str frame-number "-f") ; i guess -f for -frame?
               ;; af is inconsistent with active-attr FYI
               :class (str "ps"
                           (when (= frame-number active-frame) " active-attr af")
                           (comment (when ((:used-frames @state) frame-number) " bright-text")))}
              (str " " hex " ")])))

(defn track-map
  "a high-level view of which 'pages' have notes in the track;
  basically, it's like a high-level source view in a text editor"
  [])

(defn line
  "One line consisting of: the line number, the slice, and the high-level view"
  [slice line-id line-number line-active? window-index state]
  [:pre.slice {:id line-id}
   [:span      {:class (when line-active? :active-line)}
    [line-hex line-number]
    (let [active-chan (if line-active?
                        (:active-chan @state)
                        -1)]
      (for [[attrs chan-number] (map vector slice (range))
            :let [chan-active? (and line-active?
                                    (= chan-number active-chan))
                  chan-id (str line-number "-" chan-number)]]
        ^{:key chan-id}
        [channel attrs chan-id chan-active? state]))]
   (comment [frame-hex line-number window-index state])])

(defn main-ui
  "Main UI. Combines scheme, track, controls, modeline, etc"
  [scheme slices state]
  (let [active-line (:active-line @state)
        active-frame (quot active-line const/frame-length)]
    [:div#main-ui
     [:div#tracker
      [scheme-line scheme]
      [:div#slices
       (let [; `view` is the 'visible' portion of the track
             ;; TODO move this to startup portion
             ;; add a :view-start and :view-end to state
             ;; make set-cursor-position! set :view-start and :view-end when necessary
             ;; (still need a way to handle resize)
             ;; make the cursor movement actions set the next visible section
             ;; this is the only way to have a sliding viewport as well as a
             ;; visibility margin... well, you could do something a bit more
             ;; pubsub to have set cursor + set view limits be different things
             ;; but set-cursor-position! might as well do it for now :/
             ; [view-start
             ;  view-end] (u/bounded-range
             ;             (:active-line @state)
             ;             (quot const/frame-length 2)
             ;             (count (:slices @state))
             ;             0)
             ;; XXX temporary? who knows
             view-start (:view-start @state)
             view-end (:view-end @state)
             view (subvec (:slices @state) view-start view-end)]
         (for [[slice line-number window-index]
               (map vector view (range view-start view-end) (range))
               :let [line-id (str "line-" line-number)]]
           ^{:key line-id}
           [line slice
            line-id
            line-number
            (= line-number active-line)
            window-index  ; hax for frame; it's the index of the line in the "window"
            state]))]
      [modeline
       (str " bpm" (:bpm @state)
            " octave" (:octave @state))
       state]]]))
