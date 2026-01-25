# Introduction

[![Clojars Project](https://clojars.org/doughamil/threeagent/latest-version.svg)](https://clojars.org/doughamil/threeagent)

[Threeagent](https://github.com/DougHamil/threeagent) is a ClojureScript library for creating reactive, 3D web applications. It combines the power of [three.js](https://threejs.org) with the simplicity of [Reagent](https://reagent-project.github.io/), making it easy and fun to build 3D apps.

Threeagent uses Three.js's `WebGPURenderer`, which automatically falls back to WebGL 2 on browsers without WebGPU support.

* Familiar [hiccup](https://github.com/weavejester/hiccup)-like syntax
* Reactive-atoms for state management
* Auto-reconciliation of the Three.js scene-graph
* Escape-hatches for accessing Three.js objects
* Extendable with custom [systems](/doc/systems.md) and [entity-types](/doc/entity_type.md)

If you are familiar with Reagent, then you should find it easy to get started with Threeagent!

# Example


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
