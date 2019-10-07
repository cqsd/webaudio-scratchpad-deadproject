(ns chipper.state.commands
  (:require [chipper.state.save-load :as save-load]
            [chipper.state.primitives :as p]
            [chipper.utils :as utils]
            [clojure.string :as s]))

;; command mode relies on the :command-* attributes of the core state
;; :command-buffer self-explanatory, see actions.
;; :command-history is a vector of all previous commands, with newer commands
;; at the end
;; :command-history-index is a reverse index for the command history vector (0
;; refers to the last position). This is used for scrollback

;; This is a custom keyboard event handler, see keyboards.cljs, command mode mapping,
;; :custom mapping
;; chipper.actions/handle-custom! dispatches to this
(defn handle-command-buffer!
  "Append a character to the command buffer if it's a printable one. Note that
  backspace is handled as its own thing, for some reason. See below in
  command-handlers."  ; TODO this is refactorable
  [ev state]
  (let [buf   (:command-buffer @state)
        input (.-key ev)]
  (when (utils/is-printable? input)
    (p/set-command-buffer! (str buf input) state))))

(defn push-history-and-clear-buffer!
  "Append the command buffer to history and reset the history scrollback index.
  Return the command buffer and history in a vector."
  ([buf history state]
   (swap! state assoc
          :command-history (conj history buf)
          :command-history-index 0
          :command-buffer "")
   [buf history])

  ([state]
   (let [buf (:command-buffer @state)
         history (:command-history @state)]
     (push-history-and-clear-buffer! buf history state))))

(declare runnable-commands)

;; TODO refactor the set-mode! stuff; make these commands just return a string
;; or nil, string meaning show this string as info, nil meaning go to normal
(defn save-cmd! [state _]
  (save-load/save! state)
  (p/show-info! "saving complete" state))

(defn bpm-cmd! [state [bpm- & _]]
  (let [bpm (js/parseInt bpm-)]
    ;; this is mostly just to get rid of NaN case, set-bpm has its own guard
    ;; against non-positive bpm
    (when (pos? bpm)
      (p/set-bpm! bpm state))
    (p/set-mode! :normal state)))

(defn basefreq-cmd! [state [frequency & _]])

;; I think it might be better to open the manual page when this is typed?
(defn help-cmd! [state command-name]
  (p/show-info! (s/join "|" (map name (keys runnable-commands))) state))

(def runnable-commands
  {:save     save-cmd!
   :bpm      bpm-cmd!
   :basefreq basefreq-cmd!
   :help     help-cmd!})

;; This referes to keyboard events for command mode; it's a custom property handler
;; for the :command property (see keyboards.cljs, command mode mappings)
;; The :run handler in this map is what dispatches actual commands from the command
;; buffer
;; This is a first take at this written on a train, refactor is a TODO
(def command-handlers
  {; note that these write/open things don't take args like normal vim
   :run           (fn [state]
                    (let [[buf _] (push-history-and-clear-buffer! state)]
                      ; command as a string
                      (let [[command- & args] (s/split buf #" ")]
                        ; convert to command as a keyword to get from the internal
                        ; mapping
                        (if-let [command (get runnable-commands (keyword command-))]
                          (command state args)
                          (do (push-history-and-clear-buffer! state)
                              (p/set-mode! :normal state))))))

   :clear-buffer  (fn [state]
                    (p/set-command-buffer! "" state)
                    (p/set-command-history-index! 0 state))

   :history-older (fn [state]
                    (let [buf            (:command-buffer @state)
                          history        (:command-history @state)
                          index          (:command-history-index @state)
                          history-length (count history)
                          last-el-idx    (dec history-length)
                          reverse-index  (- last-el-idx index)]
                      ;; note that we're treating history like a stack by reversing
                      ;; the vector before we index it; ie, index 0 is actually
                      ;; the last item in history
                      (do (p/set-command-buffer!
                            (get history reverse-index "")
                            state)
                          (p/set-command-history-index!
                            (min last-el-idx (inc index))
                            state))))

   :history-newer (fn [state]
                    (let [buf            (:command-buffer @state)
                          history        (:command-history @state)
                          index          (:command-history-index @state)
                          history-length (count history)
                          last-el-idx    (dec history-length)
                          reverse-index  (inc (- last-el-idx index))]
                      ;; note that we're treating history like a stack by reversing
                      ;; the vector before we index it; ie, index 0 is actually
                      ;; the last item in history
                      (do (p/set-command-buffer!
                            (get history reverse-index "")
                            state)
                          (p/set-command-history-index!
                            (max 0 (dec index)) ; -1 will be nil, ie blank input
                            state))))

   :backspace     (fn [state]
                    (let [buf (:command-buffer @state)]
                      (p/set-command-buffer!
                        (subs buf 0 (dec (count buf)))
                        state)
                      (prn (:command-buffer @state))))})

(defn handle-command!
  [action state]
  ;; note: this is one level simpler than property-handlers
  (prn action)
  (when-let [handler (command-handlers action)]
    (handler state)))

(defn is-valid-command?
  "Used for cosmetics in the ui. Given a string, return true if a command
  with name matching that string has been defined"
  [s]
  (not (nil? (get runnable-commands (keyword s)))))
