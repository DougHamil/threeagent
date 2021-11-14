# Quick Start

Here's an example namespace that sets up a Threeagent scene:
```clojure
(ns my-app
  (:require [threeagent.core :as th]))
  
(defonce state (th/atom {:stack-size 0}))

(defn stack-of-boxes [height]
  [:object
    (for [i (range height)]
      [:box {:position [0 i 0]}])])

(defn root []
  [:object {:position [0 0 -4.0]}
    [:ambient-light {:intensity 0.8}]
    [stack-of-boxes @(th/cursor state [:stack-size])]])
           
(defn ^:dev/after-load reload 
  "Called after each hot-reload"
  []
  (th/render root (.-body js/document)))
           
(defn init 
  "Application entry-point" 
  []
  ;; Update stack-size every 10 seconds
  (.setInterval js/window #(swap! state update :stack-size inc) 10)
  (reload))
```
