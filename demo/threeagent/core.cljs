(ns threeagent.demo.core
  (:require [threeagent.alpha.core :as th]
            [THREE]))


(enable-console-print!)

(defonce state (th/atom {:rotation 0
                         :time 0
                         :color-1 "red"
                         :font nil}))

(defn on-before-render [delta-time]
  (swap! state update :rotation #(+ 0.01 %))
  (swap! state update :time #(+ delta-time %)))

(defn box [color]
  [:box {:dims [1 1 1]
         :material {:color color}}])

(defn counter []
  (let [c (atom 0)]
    (.setInterval js/window #(swap! c inc) 2000)
    (fn []
      (if (:font @state)
        [:text {:text (str "Count: " @c)
                :height 0.25
                :size 1.5
                :font (:font @state)}]
        [:object]))))

(def count-total-boxes (atom 0))
(def count-total-spheres (atom 0))

(defn- inc-count! [n a]
  (swap! a inc)
  (println "Added " n ":" @a))

(defn- dec-count! [n a]
  (swap! a dec)
  (println "Removed " n ":" @a))

(defn add-remove-check [a]
  (fn [a]
    [:object
     ^{:on-added #(inc-count! "Box" count-total-boxes)
       :on-removed #(dec-count! "Box" count-total-boxes)}
     [:box]
     (when (= (mod (int a) 4) 0)
      ^{:on-added #(inc-count! "Sphere" count-total-spheres)
        :on-removed #(dec-count! "Sphere" count-total-spheres)}
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
                            (+ -2 (.sin js/Math (+ i (/ time 15))))
                            0]}])]))

(defn root []
  [:object {:position [0 0 0]}
   [:point-light {:position [0 5 10]
                  :color 0xFFFFFF
                  :intensity 2.0}]
   [add-remove-check-wrapper]
   [:object {:position [0 2 -10]}
    [counter]]
   [:object {:rotation [0 (:rotation @state) 0]
             :position [0 0 -10]}
    [:object {:position [2.25 0 0]}
      [wave "red" 100]]
    [:object {:position [2 0 0]}
      [wave "blue" 100]]
    [:object {:position [2.5 0 0]}
      [wave "white" 100]]
    (when (:font @state)
      [:text {:text "Hello, THREEagent!"
              :position [-8 0 0]
              :rotation [0 0 0]
              :font (:font @state)
              :height 0.25
              :material {:color "white"}
              :size 1.5}])]])

(defonce scene (th/render root
                          (.-body js/document)
                          {:on-before-render on-before-render}))

(defn on-js-reload []
  (th/reload-scene scene [root] {:on-before-render on-before-render}))

(let [loader (new js/THREE.FontLoader)]
  (.load loader "fonts/helvetiker_bold.typeface.json" #(do
                                                         (println "Font loaded")
                                                         (swap! state assoc :font %))))
(comment
  (swap! state assoc :color-1 "blue"))
