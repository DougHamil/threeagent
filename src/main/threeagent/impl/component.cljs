(ns threeagent.impl.component
  (:require [threeagent.impl.threejs :as threejs]
            [threeagent.component :as component]
            [threeagent.impl.util :refer [pi-times-2 pi pi-over-2]]
            ["three" :as three])
  (:require-macros [threeagent.macros :refer [defcomponent]]))

(defonce ^:private material-cache (atom {}))

(defn render-component [key config]
  (let [renderer (get component/*registry* key)]
    (if renderer
      (renderer config)
      (println "Missing renderer for object type" key))))

(defn- cached-material [cfg]
  (if-let [m (get @material-cache cfg)]
    m
    (let [m (threejs/mesh-phong-material cfg)]
      (swap! material-cache assoc cfg m)
      m)))

(defn- ->material [config]
  (if (instance? three/Material config)
    config
    (cached-material config)))

(defn- to-mesh [geo material-config]
  (let [mat (->material material-config)]
    (threejs/mesh geo mat)))

;; Basic
(defcomponent :object [c] (threejs/object))
(defcomponent :group [c] (threejs/group))

(defcomponent :perspective-camera [{:keys [fov aspect near far active]
                                    :or {fov 75.0
                                         aspect 1.0
                                         near 0.1
                                         far 2000.0
                                         active true}}]
  (let [cam (three/PerspectiveCamera. fov aspect near far)]
    (set! (.-active cam) active)
    cam))

(defcomponent :orthographic-camera [{:keys [left right top bottom near far active]
                                     :or {near 0.1
                                          far 2000.0
                                          active true}}]
  (let [cam (three/OrthographicCamera. left right top bottom near far)]
    (set! (.-active cam) active)
    cam))

(defcomponent :instance [{:keys [object]}] object)

;; Primitives
(defcomponent :plane [{:keys [width height
                              width-segments height-segments
                              material]}]
  (let [geo (three/PlaneGeometry. width height width-segments height-segments)]
    (to-mesh geo material)))

(defcomponent :box [{:keys [width height depth width-segments
                            height-segments depth-segments
                            material]
                     :or {width 1.0
                          height 1.0
                          depth 1.0
                          width-segments 1
                          height-segments 1
                          depth-segments 1}}]
  (let [geo (three/BoxGeometry. width height depth
                                width-segments height-segments depth-segments)]
    (to-mesh geo material)))
    
(defcomponent :sphere [{:keys [radius width-segments height-segments
                               phi-start phi-length theta-start theta-length
                               material]
                        :or {radius 1.0
                             width-segments 8
                             height-segments 6
                             phi-start 0
                             phi-length pi-times-2
                             theta-start 0
                             theta-length pi}}]
  (let [geo (three/SphereGeometry. radius
                                   width-segments
                                   height-segments
                                   phi-start
                                   phi-length
                                   theta-start
                                   theta-length)]
    (to-mesh geo material)))

(defcomponent :cylinder [{:keys [radius-top radius-bottom height radial-segments
                                 height-segments open-ended? theta-start theta-length
                                 material]
                          :or {radius-top 1.0
                               radius-bottom 1.0
                               height 1.0
                               radial-segments 8
                               height-segments 1
                               theta-start 0
                               theta-length pi-times-2}}]
  (let [geo (three/CylinderGeometry. radius-top 
                                     radius-bottom 
                                     height 
                                     radial-segments
                                     height-segments
                                     open-ended?
                                     theta-start
                                     theta-length)]
    (to-mesh geo material)))

(defcomponent :circle [{:keys [radius segments theta-start theta-length material]
                        :or {radius 1.0
                             segments 8
                             theta-start 0
                             theta-length pi-times-2}}]
  (let [geo (three/CircleGeometry. radius
                                   segments
                                   theta-start
                                   theta-length)]
    (to-mesh geo material)))

(defcomponent :cone [{:keys [radius height radial-segments height-segments
                             open-ended? theta-start theta-length material]
                      :or {radius 1.0
                           height 1.0
                           radial-segments 8
                           height-segments 1
                           theta-start 0
                           theta-length pi-times-2}}]
  (let [geo (three/ConeGeometry. radius height
                                 radial-segments height-segments
                                 open-ended?
                                 theta-start theta-length)]
    (to-mesh geo material)))

(defcomponent :dodecahedron [{:keys [radius detail material]
                              :or {radius 1.0
                                   detail 0}}]
  (let [geo (three/DodecahedronGeometry. radius detail)]
    (to-mesh geo material)))

(defcomponent :icosahedron [{:keys [radius detail material]
                             :or {radius 1.0
                                  detail 0}}]
  (let [geo (three/IcosahedronGeometry. radius detail)]
    (to-mesh geo material)))

(defcomponent :octahedron [{:keys [radius detail material]
                            :or {radius 1.0
                                 detail 0}}]
  (let [geo (three/OctahedronGeometry. radius detail)]
    (to-mesh geo material)))

(defcomponent :ring [{:keys [inner-radius outer-radius
                             theta-segments phi-segments
                             theta-start theta-length
                             material]
                      :or {inner-radius 0.5
                           outer-radius 1.0
                           theta-segments 8
                           phi-segments 8
                           theta-start 0
                           theta-length pi-times-2}}]
                           
  (let [geo (three/RingGeometry. inner-radius outer-radius
                                 theta-segments phi-segments
                                 theta-start theta-length)]
    (to-mesh geo material)))

(defcomponent :tetrahedron [{:keys [radius detail material]
                             :or {radius 1.0
                                  detail 0}}]
  (let [geo (three/TetrahedronGeometry. radius detail)]
    (to-mesh geo material)))

(defcomponent :torus [{:keys [radius tube radial-segments tubular-segments arc
                              material]
                       :or {radius 1.0
                            tube 0.4
                            radial-segments 8
                            tubular-segments 6
                            arc pi-times-2}}]
  (let [geo (three/TorusGeometry. radius tube radial-segments tubular-segments arc)]
    (to-mesh geo material)))

(defcomponent :torus-knot [{:keys [radius tube tubular-segments radial-segments p q
                                   material]
                            :or {radius 1.0
                                 tube 0.4
                                 tubular-segments 64
                                 radial-segments 8
                                 p 2
                                 q 3}}]
  (let [geo (three/TorusKnotGeometry. radius tube tubular-segments radial-segments p q)]
    (to-mesh geo material)))

(defcomponent :shape [{:keys [shape material]}]
  (let [geo (three/ShapeGeometry. shape)]
    (to-mesh geo material)))


;; Lights
(defcomponent :ambient-light [{:keys [color intensity]
                               :or {color 0xFFFFFF
                                    intensity 1.0}}]
  (three/AmbientLight. color intensity))

(defcomponent :point-light [{:keys [color intensity distance decay]
                             :or {color 0xFFFFFF
                                  intensity 1.0
                                  distance 0
                                  decay 1.0}}]
  (three/PointLight. color intensity distance decay))

(defcomponent :hemisphere-light [{:keys [sky-color ground-color intensity]
                                  :or {sky-color 0xFFFFFF
                                       ground-color 0xFFFFFF
                                       intensity 1}}]
  (three/HemisphereLight. sky-color ground-color intensity))

(defcomponent :directional-light [{:keys [color intensity]
                                   :or {color 0xFFFFFF
                                        intensity 1.0}}]
  (three/DirectionalLight. color intensity))

(defcomponent :rect-area-light [{:keys [color intensity width height]
                                 :or {color 0xFFFFFF
                                      intensity 1.0
                                      width 10.0
                                      height 10.0}}]
  (three/RectAreaLight. color intensity width height))

(defcomponent :spot-light [{:keys [color intensity distance angle penumbra decay]
                            :or {color 0xFFFFFF
                                 intensity 1.0
                                 distance 0
                                 angle pi-over-2
                                 penumbra 0.0
                                 decay 1.0}}]
  (three/SpotLight. color intensity distance angle penumbra decay))


;; Text
(defcomponent :text [{:keys [text material] :as cfg}]
  (let [geo (threejs/text-geometry text cfg)]
    (to-mesh geo material)))

