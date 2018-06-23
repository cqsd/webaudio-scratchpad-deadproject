(defproject chipper "0.1.0a"
  :description "In-browser chiptune tracker."
  :url "http://github.com/cqsd/chipper"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :min-lein-version "2.7.1"
  :dependencies [[reagent "0.8.1"]
                 [org.clojure/clojure "1.9.0"]
                 [org.clojure/clojurescript "1.10.312"]
                 [org.clojure/core.async "0.4.474"
                  :exclusions [org.clojure/tools.reader]]]
  :repl-options {:nrepl-middleware [cider.piggieback/wrap-cljs-repl]} ;; fireplace
  :plugins [[lein-figwheel "0.5.16"]
            [lein-cljsbuild "1.1.7" :exclusions [[org.clojure/clojure]]]]
  :source-paths ["src"]
  :clean-targets ^{:protect false} ["resources/public/js/compiled" "target"]
  :cljsbuild {:builds
              [{:id "dev"
                :source-paths ["src"]
                ;; inject figwheel into cljs build
                :figwheel {:on-jsload "chipper.core/on-js-reload"}
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
                           ;http://blog.alex-turok.com/2016/05/using-external-javascript-library-in.html
                           :externs ["resources/public/js/externs.js"]
                           :optimizations :advanced
                           :pretty-print false}}]}
  :figwheel {:css-dirs ["resources/public/css"] ;; watch and update CSS
             :nrepl-port 7888
             ;; :ring-handler hello_world.server/handler
             }
 
  :profiles {:dev {:dependencies [[org.clojure/tools.nrepl "0.2.13"]
                                  [binaryage/devtools "0.8.2"]
                                  [figwheel-sidecar "0.5.16"]
                                  [cider/piggieback "0.3.6"]]
                   ;; need to add dev source path here to get user.clj loaded
                   :source-paths ["src" "dev"]
                   ;; for CIDER
                   :plugins [[cider/cider-nrepl "0.17.0"]]
                   :repl-options {; for nREPL dev you really need to limit output
                                  :init (set! *print-length* 50)
                                  :nrepl-middleware [cider.piggieback/wrap-cljs-repl]}}})
