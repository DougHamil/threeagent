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

(defn- to-mesh [geo material-config cast-shadow receive-shadow]
  (let [mat (->material material-config)
        mesh ^js (threejs/mesh geo mat)]
    (set! (.-castShadow mesh) cast-shadow)
    (set! (.-receiveShadow mesh) receive-shadow)
    mesh))

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
                              cast-shadow
                              receive-shadow
                              material]
                       :or {width 1
                            height 1
                            width-segments 1
                            height-segments 1
                            cast-shadow false
                            receive-shadow false}}]
  (let [geo (three/PlaneGeometry. width height width-segments height-segments)]
    (to-mesh geo material cast-shadow receive-shadow)))

(defcomponent :box [{:keys [width height depth width-segments
                            height-segments depth-segments
                            cast-shadow
                            receive-shadow
                            material]
                     :or {width 1.0
                          height 1.0
                          depth 1.0
                          width-segments 1
                          height-segments 1
                          depth-segments 1
                          cast-shadow false
                          receive-shadow false}}]
  (let [geo (three/BoxGeometry. width height depth
                                width-segments height-segments depth-segments)]
    (to-mesh geo material cast-shadow receive-shadow)))
    
(defcomponent :sphere [{:keys [radius width-segments height-segments
                               phi-start phi-length theta-start theta-length
                               cast-shadow
                               receive-shadow
                               material]
                        :or {radius 1.0
                             width-segments 8
                             height-segments 6
                             phi-start 0
                             phi-length pi-times-2
                             theta-start 0
                             theta-length pi
                             cast-shadow false
                             receive-shadow false}}]
  (let [geo (three/SphereGeometry. radius
                                   width-segments
                                   height-segments
                                   phi-start
                                   phi-length
                                   theta-start
                                   theta-length)]
    (to-mesh geo material cast-shadow receive-shadow)))

(defcomponent :cylinder [{:keys [radius-top radius-bottom height radial-segments
                                 height-segments open-ended? theta-start theta-length
                                 cast-shadow receive-shadow
                                 material]
                          :or {radius-top 1.0
                               radius-bottom 1.0
                               height 1.0
                               radial-segments 8
                               height-segments 1
                               open-ended? false
                               cast-shadow false
                               receive-shadow false
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
    (to-mesh geo material cast-shadow receive-shadow)))

(defcomponent :circle [{:keys [radius segments theta-start theta-length material
                               cast-shadow receive-shadow]
                        :or {radius 1.0
                             segments 8
                             cast-shadow false
                             receive-shadow false
                             theta-start 0
                             theta-length pi-times-2}}]
  (let [geo (three/CircleGeometry. radius
                                   segments
                                   theta-start
                                   theta-length)]
    (to-mesh geo material cast-shadow receive-shadow)))

(defcomponent :cone [{:keys [radius height radial-segments height-segments
                             open-ended? theta-start theta-length material
                             cast-shadow receive-shadow]
                      :or {radius 1.0
                           height 1.0
                           cast-shadow false
                           receive-shadow false
                           radial-segments 8
                           height-segments 1
                           theta-start 0
                           theta-length pi-times-2}}]
  (let [geo (three/ConeGeometry. radius height
                                 radial-segments height-segments
                                 open-ended?
                                 theta-start theta-length)]
    (to-mesh geo material cast-shadow receive-shadow)))

(defcomponent :dodecahedron [{:keys [radius detail material
                                     cast-shadow receive-shadow]
                              :or {radius 1.0
                                   cast-shadow false
                                   receive-shadow false
                                   detail 0}}]
  (let [geo (three/DodecahedronGeometry. radius detail)]
    (to-mesh geo material cast-shadow receive-shadow)))

(defcomponent :icosahedron [{:keys [radius detail material
                                    cast-shadow receive-shadow]
                             :or {radius 1.0
                                  cast-shadow false
                                  receive-shadow false
                                  detail 0}}]
  (let [geo (three/IcosahedronGeometry. radius detail)]
    (to-mesh geo material cast-shadow receive-shadow)))

(defcomponent :octahedron [{:keys [radius detail material
                                   cast-shadow receive-shadow]
                            :or {radius 1.0
                                 cast-shadow false
                                 receive-shadow false
                                 detail 0}}]
  (let [geo (three/OctahedronGeometry. radius detail)]
    (to-mesh geo material cast-shadow receive-shadow)))

(defcomponent :ring [{:keys [inner-radius outer-radius
                             theta-segments phi-segments
                             theta-start theta-length
                             cast-shadow receive-shadow
                             material]
                      :or {inner-radius 0.5
                           outer-radius 1.0
                           theta-segments 8
                           phi-segments 8
                           cast-shadow false
                           receive-shadow false
                           theta-start 0
                           theta-length pi-times-2}}]
                           
  (let [geo (three/RingGeometry. inner-radius outer-radius
                                 theta-segments phi-segments
                                 theta-start theta-length)]
    (to-mesh geo material cast-shadow receive-shadow)))

(defcomponent :tetrahedron [{:keys [radius detail material
                                    cast-shadow receive-shadow]
                             :or {radius 1.0
                                  cast-shadow false
                                  receive-shadow false
                                  detail 0}}]
  (let [geo (three/TetrahedronGeometry. radius detail)]
    (to-mesh geo material cast-shadow receive-shadow)))

(defcomponent :torus [{:keys [radius tube radial-segments tubular-segments arc
                              material cast-shadow receive-shadow]
                       :or {radius 1.0
                            tube 0.4
                            cast-shadow false
                            receive-shadow false
                            radial-segments 8
                            tubular-segments 6
                            arc pi-times-2}}]
  (let [geo (three/TorusGeometry. radius tube radial-segments tubular-segments arc)]
    (to-mesh geo material cast-shadow receive-shadow)))

(defcomponent :torus-knot [{:keys [radius tube tubular-segments radial-segments p q
                                   material cast-shadow receive-shadow]
                            :or {radius 1.0
                                 tube 0.4
                                 tubular-segments 64
                                 radial-segments 8
                                 cast-shadow false
                                 receive-shadow false
                                 p 2
                                 q 3}}]
  (let [geo (three/TorusKnotGeometry. radius tube tubular-segments radial-segments p q)]
    (to-mesh geo material cast-shadow receive-shadow)))

(defcomponent :shape [{:keys [shape material cast-shadow receive-shadow]}]
  (let [geo (three/ShapeGeometry. shape)]
    (to-mesh geo material cast-shadow receive-shadow)))


;; Lights
(def ^:private default-light-shadow {:map-size {:width 512
                                                :height 512}
                                     :focus 1.0
                                     :camera {:near 0.5
                                              :far 500
                                              :left -5
                                              :right 5
                                              :top 5
                                              :bottom -5}})
(defn- apply-shadow-settings [light shadow-cfg]
  (when shadow-cfg
    (let [shadow (.-shadow light)
          camera-obj (.-camera shadow)
          map-size (merge (:map-size default-light-shadow)
                          (:map-size shadow-cfg))
          camera (merge (:camera default-light-shadow)
                        (:camera shadow-cfg))]
      (set! (.-width (.-mapSize shadow)) (:width map-size))
      (set! (.-height (.-mapSize shadow)) (:height map-size))
      (set! (.-near camera-obj) (:near camera))
      (set! (.-far camera-obj) (:far camera))
      (set! (.-left camera-obj) (:left camera))
      (set! (.-right camera-obj) (:right camera))
      (set! (.-top camera-obj) (:top camera))
      (set! (.-bottom camera-obj) (:bottom camera))
      (set! (.-focus shadow) (or (:focus shadow-cfg)
                                 (:focus default-light-shadow)))
      (.updateProjectionMatrix camera-obj)))
  light)

(defcomponent :ambient-light [{:keys [color intensity]
                               :or {color 0xFFFFFF
                                    intensity 1.0}}]
  (three/AmbientLight. color intensity))

(defcomponent :point-light [{:keys [color intensity distance decay
                                    cast-shadow shadow]
                             :or {color 0xFFFFFF
                                  cast-shadow false
                                  shadow nil
                                  intensity 1.0
                                  distance 0
                                  decay 1.0}}]
  (apply-shadow-settings (three/PointLight. color intensity distance decay)
                         shadow))

(defcomponent :hemisphere-light [{:keys [sky-color ground-color intensity]
                                  :or {sky-color 0xFFFFFF
                                       ground-color 0xFFFFFF
                                       intensity 1}}]
  (three/HemisphereLight. sky-color ground-color intensity))

(defcomponent :directional-light [{:keys [color intensity
                                          target
                                          cast-shadow shadow]
                                   :or {color 0xFFFFFF
                                        shadow nil
                                        intensity 1.0}}]
  (let [light ^js (three/DirectionalLight. color intensity)]
    (when target
      (set! (.-target light) target))
    (apply-shadow-settings light shadow)))

(defcomponent :rect-area-light [{:keys [color intensity width height]
                                 :or {color 0xFFFFFF
                                      intensity 1.0
                                      width 10.0
                                      height 10.0}}]
  (three/RectAreaLight. color intensity width height))

(defcomponent :spot-light [{:keys [color intensity distance angle penumbra decay
                                   shadow cast-shadow]
                            :or {color 0xFFFFFF
                                 cast-shadow false
                                 shadow nil
                                 intensity 1.0
                                 distance 0
                                 angle pi-over-2
                                 penumbra 0.0
                                 decay 1.0}}]
  (apply-shadow-settings (three/SpotLight. color intensity distance angle penumbra decay)
                         shadow))


;; Text
(defcomponent :text [{:keys [text material
                             font size height
                             curve-segments bevel-enabled
                             bevel-thickness bevel-size
                             bevel-offset bevel-segments
                             cast-shadow receive-shadow]
                      :or {cast-shadow false
                           receive-shadow false
                           size 100
                           height 50
                           curve-segments 12
                           bevel-enabled false
                           bevel-thickness 10
                           bevel-size 8
                           bevel-offset 0
                           bevel-segments 3}}]
  (let [geo (threejs/text-geometry text {:font font
                                         :size size
                                         :height height
                                         :curveSegments curve-segments
                                         :bevelEnabled bevel-enabled
                                         :bevelThickness bevel-thickness
                                         :bevelSize bevel-size
                                         :bevelOffset bevel-offset
                                         :bevelSegments bevel-segments})]
    (to-mesh geo material cast-shadow receive-shadow)))

