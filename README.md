## Chipper
In-browser music tracker

### Build
`lein cljsbuild once min`, load `resources/public/index.html`.

Note: if cljsbuild exits with no output, and it appears that nothing has changed
in the compiled js, remove `resources/public/js/compiled` and
`resources/public/js/chipper.js` and try again.

### Develop
`lein fighweel`, load `localhost:3449`.
