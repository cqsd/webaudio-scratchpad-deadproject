## Chipper
In-browser chiptune tracker.

Build with `lein cljsbuild once min`, load `resources/public/index.html`.

Start a dev repl with `lein repl`. Once it starts up, run `(lets-go)` (defined
in `dev/user.clj`) to start figwheel:
```
user=> (lets-go)
```
When you see this,
```
user=> (lets-go)
Figwheel: Starting server at http://0.0.0.0:3449
Figwheel: Watching build - dev
Compiling "resources/public/js/chipper.js" from ["src"]...
Successfully compiled "resources/public/js/chipper.js" in 8.339 seconds.
Figwheel: Starting CSS Watcher for paths  ["resources/public/css"]
Figwheel: Starting nREPL server on port: 7888
Launching ClojureScript REPL for build: dev
Figwheel Controls:
          (stop-autobuild)                ;; stops Figwheel autobuilder
          (start-autobuild [id ...])      ;; starts autobuilder focused on optional ids
          (switch-to-build id ...)        ;; switches autobuilder to different build
          (reset-autobuild)               ;; stops, cleans, and starts autobuilder
          (reload-config)                 ;; reloads build config and resets autobuild
          (build-once [id ...])           ;; builds source one time
          (clean-builds [id ..])          ;; deletes compiled cljs target files
          (print-config [id ...])         ;; prints out build configurations
          (fig-status)                    ;; displays current state of system
  Switch REPL build focus:
          :cljs/quit                      ;; allows you to switch REPL to another build
    Docs: (doc function-name-here)
    Exit: Control+C or :cljs/quit
 Results: Stored in vars *1, *2, *3, *e holds last exception object
Prompt will show when Figwheel connects to your application
```
you're ready to develop. Open `localhost:3449` to connect the cljs repl with your
browser and get live code reloads (and whatever the hell else it does because I
really don't remember)
