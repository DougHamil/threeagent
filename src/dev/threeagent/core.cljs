(ns threeagent.dev.core
  (:require [threeagent.alpha.core :as th]
            ["three" :as three]))

(enable-console-print!)

(defonce state (th/atom {:time 0}))

(def test-shape (doto (three/Shape.)
                  (.moveTo 25 25)
                  (.bezierCurveTo 25 25 20 0 0 0)
                  (.bezierCurveTo 30 0 30 35 30 35)
                  (.bezierCurveTo 30 55 10 77 25 95)
                  (.bezierCurveTo 60 77 80 55 80 35)
                  (.bezierCurveTo 80 35 80 0 50 0)
                  (.bezierCurveTo 35 0 25 25 25 25)))

(defn tick [delta-time]
  (swap! state update :time + delta-time))

(defn gridify [spacing per-row objs]
  [:object
   (for [[i o] (map-indexed vector objs)]
     [:object {:position [(* spacing (mod i per-row))
                          (* (- spacing) (int (/ i per-row)))
                          0]}
      o])])

(defn primitives []
  (let [objs [[:box]
              [:sphere]
              [:cylinder]
              [:circle]
              [:plane]
              [:cone]
              [:dodecahedron]
              [:icosahedron]
              [:octahedron]
              [:ring]
              [:tetrahedron]
              [:torus]
              [:torus-knot]
              [:shape {:shape test-shape
                       :rotation [0 0 js/Math.PI]
                       :position [1 1 0]
                       :scale [0.03 0.03 0.03]}]]]
    (gridify 3.0 5 objs)))

(defn root []
  [:object {:position [0 0 -10]}
   [:ambient-light {:color 0x8800BB}]
   [:hemisphere-light {:intensity 0.3}]
   [:point-light {:position [0 5 10]
                  :color 0xFFFFFF
                  :intensity 2.0}]
   [:object {:position [-8 4 0]}
    [primitives]]])


(def ^:private ^:dynamic *scene* nil)

(defn on-js-reload []
  (set! *scene* (th/render root
                           (.getElementById js/document "root")
                           {:on-before-render tick})))

(on-js-reload)
