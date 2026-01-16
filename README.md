# threeagent
[![CircleCI](https://circleci.com/gh/DougHamil/threeagent/tree/main.svg?style=svg)](https://circleci.com/gh/DougHamil/threeagent/tree/main)
[![cljdoc badge](https://cljdoc.org/badge/doughamil/threeagent)](https://cljdoc.org/d/doughamil/threeagent)

ClojureScript library for building Three.js apps in a reagent-like fashion

## Installation
[![Clojars Project](https://clojars.org/doughamil/threeagent/latest-version.svg)](https://clojars.org/doughamil/threeagent)

threeagent depends on THREE.js, so it will need to be added as a dependency to your project as well.

For shadow-cljs, install THREE.js using npm:
```
npm install --save three
```

For lein-cljsbuild, add an npm-deps entry on THREE.js:
```clojure
:cljsbuild {:builds [{...
                      :compiler {...
                                 :install-deps true
                                 :npm-deps {:three "0.100.0"}}}]}
```

## Example Apps

[Zombie Shooter](https://doughamil.github.io/zombie-threeagent-example/)

[Pong](https://doughamil.github.io/threeagent-examples/pong/)

[Beat Saber Map Editor](https://doughamil.github.io/threeagent-examples/beatsajer/)

[Tetris](https://doughamil.github.io/threeagent-examples/tetris/)
 
## Example Code
```clojure
(ns my-app.core
  (:require [threeagent.core :as th]))
  
;; Use reactive atom for storing state
(defonce state (th/atom {:ticks 0}))

;; Tick every second
(.setInterval js/window #(swap! state update :ticks inc) 1000)

;; Form-1 component example
(defn color-box [color size]
  [:box {:dims [size size size]
         :material {:color color}}])
         
;; Form-2 component example
(defn growing-sphere []
  (let [s (atom 0)]
    (.setInterval js/window #(swap! s inc) 5000)
    (fn []
      [:sphere {:radius @s}])))

;; Root component render function
(defn root []
  [:object {:position [1.0 0 -4.0]
            :rotation [0 (.sin js/Math (:ticks @state)) 0]} ; Rotate on Y axis based on :ticks
    [:ambient-light {:intensity 0.8}]
    [color-box "red" 1.0] ; Don't forget to use square brackets!
    [growing-sphere]])
           
           
;; Initialize and begin rendering threeagent scene
(defonce scene (th/render root (.-body js/document)))
```

## Documentation

The latest documentation is available [here](https://cljdoc.org/d/doughamil/threeagent)

## Development

### Running Tests
Use shadow-cljs to watch unit tests

```bash
npx shadow-cljs watch test
```

Navigate to the HTTP server that shadow-cljs creates to view the test results



