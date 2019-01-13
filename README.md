# threeagent
ClojureScript library for building Three.js apps in a reagent-like fashion

## Installation
 `TODO`
 
## Getting Started
`TODO`

## Example
```clojure
(ns my-app.core
  (:require [threeagent.core :as th]))
  
;; Use reactive atom for storing state
(defonce state (th/atom {:ticks 0}))

;; Tick every second
(.setInterval js/window #(swap! state update :ticks inc) 1000)

;; Type-1 component example
(defn color-box [color size]
  [:box {:dims [size size size]
         :material {:color color}])

;; Root component render function
(defn root []
  [:object {:position [1.0 0 -4.0]
            :rotation [0 (.sin js/Math (:ticks @state)) 0]} ; Rotate on Y axis based on :ticks
    [color-box "red" 1.0]]) ; Don't forget to use square brackets!
           
           
;; Initialize and begin rendering threeagent scene
(defonce scene (th/render root (.-body js/document)))
```
