(ns threeagent.render-test.core
  (:require [threeagent.alpha.core :as th]
            ["three" :as three]))

(enable-console-print!)

(defonce state (th/atom {:rotation 0
                         :time 0
                         :color-1 "red"}))

(defn on-before-render [delta-time])

(defn box [color]
  [:box {:dims [1 1 1]
         :material {:color color}}])

(defn add-remove-check [a]
  (fn [a]
    [:object
     [:box]
     (when (= (mod (int a) 4) 0)
      [:sphere])]))

(defn add-remove-check-wrapper []
  [:object {:position [0 0 -10]}
   (let [time @(th/cursor state [:time])]
     (when (= (mod (int time) 2) 0)
      [add-remove-check time]))])

(defn wave [color count]
  (let [time @(th/cursor state [:time])]
    [:object
     (for [i (range count)]
       [:sphere {:radius 0.5
                 :material {:color color}
                 :position [(- i (/ count 2))
                            (+ -2 (.sin js/Math (+ i (* 2.0 time))))
                            0]}])]))

(defn root []
  [:object {:position [0 0 0]}
   [:point-light {:position [0 5 10]
                  :color 0xFFFFFF
                  :intensity 2.0}]
   [add-remove-check-wrapper]
   [:object {:position [0 2 -10]}
    [counter]]
   [:object {:position [0 0 -10]}
    [:object {:position [2.25 0 0]}
      [wave "red" 10]]
    [:object {:position [2 0 0]}
      [wave "blue" 10]]
    [:object {:position [2.5 0 0]}
      [wave "white" 10]]]])

(defonce scene (th/render root
                          (.getElementById js/document "root")
                          {:on-before-render on-before-render}))
