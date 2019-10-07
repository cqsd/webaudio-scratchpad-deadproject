(ns chipper.state.commands
  (:require [chipper.state.save-load :as save-load]
            [chipper.state.primitives :as p]
            [clojure.string :refer [split]]))

(defn push-history-and-clear-buffer!
  ([buf history state]
   (do (swap! state assoc
              :command-history (conj history buf)
              :command-history-index 0
              :command-buffer ""))
   [buf history])
  ([state]
   (let [buf (:command-buffer @state)
         history (:command-history @state)]
     (push-history-and-clear-buffer! buf history state))))


(defn save-cmd [state]
  (save-load/save! state)
  (p/show-info! "saving complete" state)
  (prn (:mode @state)))

(def runnable-commands
  {:save save-cmd
   :w    save-cmd
   ; this one requires a dom element... see save-load source
   ; :load (fn [state]  (save-load/load-save-file! state))
   })

(def command-handlers
  {; note that these write/open things don't take args like normal vim
   :run           (fn [state]
                    (let [[buf _] (push-history-and-clear-buffer! state)]
                      ; command as a string
                      (let [[command- arg] (split buf #" " 2)]
                        ; convert to command as a keyword to get from the internal
                        ; mapping
                        (if-let [command (get runnable-commands (keyword command-))]
                          (command state)
                          (do (push-history-and-clear-buffer! state)
                              (p/set-mode! :normal state))))))
   :exit          (fn [state]
                    (p/set-command-buffer! "" state)
                    (p/set-command-history-index! 0 state)
                    (p/set-mode! :normal state))
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

; TODO move some of this to primitives
(defn handle-command!
  [action state]
  ;; note: this is one level simpler than property-handlers
  (when-let [handler (command-handlers action)]
    (handler state)))

(defn is-valid-command?
  "used for cosmetics in the ui. given a string, return true if a command
  with name matching that string has been defined"
  [s]
  (not (nil? (get runnable-commands (keyword s)))))
