# Introduction
threeagent is a ClojureScript library for building reactive, [three.js](https://threejs.org) applications in a [Reagent](https://reagent-project.github.io/)-like fashion.

## Getting Started

Include the threeagent library in your project:

[![Clojars Project](https://clojars.org/doughamil/threeagent/latest-version.svg)](https://clojars.org/doughamil/threeagent)

threeagent depends on three.js, so it will need to be added as a dependency to your project as well.

For [shadow-cljs](http://shadow-cljs.org/), install three.js using npm:
```
npm install --save three
```

For [lein-cljsbuild](https://github.com/emezeske/lein-cljsbuild), add an npm-deps entry for three.js:
```clojure
:cljsbuild {:builds [{...
                      :compiler {...
                                 :install-deps true
                                 :npm-deps {:three "0.100.0"}}}]}
```
 
## Example
```clojure
(ns my-app.core
  (:require [threeagent.alpha.core :as th]))
  
;; Use reactive atom for storing state
(defonce state (th/atom {:ticks 0}))

;; Tick every second
(.setInterval js/window #(swap! state update :ticks inc) 1000)

;; Form-1 component example
(defn color-box [color size]
  [:box {:dims [size size size]
         :material {:color color}])
         
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
    [color-box "red" 1.0] ; Don't forget to use square brackets!
    [growing-sphere]])
           
           
;; Initialize and begin rendering threeagent scene
(defonce scene (th/render root (.-body js/document)))
```

[More example projects](https://github.com/DougHamil/threeagent-examples)

## Usage

### Share state with Reagent

### Custom Components
`defrenderer`

### THREE.js Object Instances
`:instance component`

### WebVR Support
