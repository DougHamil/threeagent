(ns shadows.app
  (:require [threeagent.core :as th]))
  
;; Use reactive atom for storing state
(defonce state (th/atom {:ticks 0}))

;; Form-1 component example
(defn color-box [color size]
  [:object {:rotation [0 (.sin js/Math (/ (:ticks @state)
                                          100))
                       0]} ; Rotate on Y axis based on :ticks
   [:box {:dims [size size size]
          :receive-shadow true
          :cast-shadow true
          :material {:color color}}]])
         
;; Root component render function
(defn root []
  [:object
   [:ambient-light {:intensity 0.2}]
   [:object {:rotation [0 0 0]
             :position [0 0 0]}
    [:point-light {:intensity   0.8
                   :position    [1 5 0]
                   :cast-shadow true}]]
   [:object {:position [1.0 0 -4.0]}
    [:plane {:scale          [10 10 1]
             :receive-shadow true}]
    [color-box "red" 1.0]]]) ; Don't forget to use square brackets!

;; Initialize and begin rendering threeagent scene
(defn ^:dev/after-load reload []
  (js/console.log
   (th/render root (.-body js/document)
              {:shadow-map {:enabled true}})))

(defn init []
  (.setInterval js/window #(swap! state update :ticks inc) 10)
  (reload))
