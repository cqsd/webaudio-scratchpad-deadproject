(defproject chipper "0.1.1"
  :description "In-browser chiptune-ish tracker."
  :url "http://github.com/cqsd/chipper"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :min-lein-version "2.7.1"
  :dependencies [[reagent "0.8.1"]
                 [org.clojure/clojure "1.10.1"]
                 [org.clojure/clojurescript "1.10.520"]
                 [org.clojure/core.async "0.4.500"
                  :exclusions [org.clojure/tools.reader]]]
  :repl-options {:nrepl-middleware [cider.piggieback/wrap-cljs-repl]} ;; fireplace
  :plugins [[lein-figwheel "0.5.19"]
            [lein-cljsbuild "1.1.7" :exclusions [[org.clojure/clojure]]]
            [lein-cljfmt "0.6.4"]]
  :source-paths ["src"]
  :clean-targets ^{:protect false} ["resources/public/js/compiled" "target"]
  :cljsbuild {:builds
              [{:id "dev"
                :source-paths ["src"]
                ;; inject figwheel into cljs build
                :figwheel true
                :compiler {:main chipper.core
                           :asset-path "js/compiled/out"
                           :output-to "resources/public/js/chipper.js"
                           :output-dir "resources/public/js/compiled/out"
                           :externs ["resources/public/js/externs.js"]
                           :source-map-timestamp true
                           ;; print clj data structures in console
                           :preloads [devtools.preload]}}
               {:id "min"
                :source-paths ["src"]
                :compiler {:output-to "resources/public/js/chipper.js"
                           :main chipper.core
                           ;; http://blog.alex-turok.com/2016/05/using-external-javascript-library-in.html
                           :externs ["resources/public/js/externs.js"]
                           :optimizations :advanced
                           :pretty-print false}}]}
  :figwheel {:css-dirs ["resources/public/css"] ;; watch and update CSS
             :nrepl-port 7888}

  :profiles {:dev {:dependencies [;[org.clojure/tools.nrepl "0.2.13"]
                                  [nrepl "0.6.0"]
                                  [binaryage/devtools "0.9.10"]
                                  [figwheel-sidecar "0.5.4-6"]
                                  [cider/piggieback "0.4.1"]]
                   :source-paths ["src"]
                   ;; for CIDER
                   :plugins [[cider/cider-nrepl "0.22.1"]]
                   :repl-options {; for nREPL dev you really need to limit output
                                  :init (set! *print-length* 50)
                                  :nrepl-middleware [cider.piggieback/wrap-cljs-repl]}}})
