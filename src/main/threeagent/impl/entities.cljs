(ns threeagent.impl.entities
  "Defines all of the built-in IEntityType types.
  This namespace is meant for internal use only, these functions are subject to change."
  (:require [threeagent.entity :refer [IEntityType IUpdateableEntityType]]
            [threeagent.impl.threejs :as threejs]
            [threeagent.impl.util :refer [pi pi-times-2 pi-over-2]]
            ["three" :as three]
            [clojure.set :refer [rename-keys]]
            [goog.object :as gobject]))
            

(defonce ^:private material-cache (memoize threejs/mesh-phong-material))

(defn- ->material [config]
  (if (instance? three/Material config)
    config
    (material-cache config)))

(deftype MeshEntity [geo-fn]
  IEntityType
  (create [_ _ config]
    (let [geo (geo-fn config)
          mat (->material (:material config))
          mesh (three/Mesh. geo mat)]
      (set! (.-castShadow mesh) (:cast-shadow config))
      (set! (.-receiveShadow mesh) (:receive-shadow config))
      mesh))
  (destroy! [_ _ _ _])
  IUpdateableEntityType
  (update! [_ _ ^three/Mesh mesh config]
    (let [geo (geo-fn config)
          mat (->material (:material config))]
      (set! (.-geometry mesh) geo)
      (set! (.-material mesh) mat)
      (set! (.-castShadow mesh) (:cast-shadow config))
      (set! (.-receiveShadow mesh) (:receive-shadow config))
      mesh)))

(def ^:private default-light-shadow {:map-size {:width 512
                                                :height 512}
                                     :focus 1.0
                                     :camera {:near 0.5
                                              :far 500
                                              :left -5
                                              :right 5
                                              :top 5
                                              :bottom -5}})

(defn- apply-shadow-settings! [light shadow-cfg]
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

(deftype LightEntity [create-fn update-fn]
  IEntityType
  (create [_ _ cfg]
    (let [light (create-fn cfg)]
      (set! (.-castShadow light) (:cast-shadow cfg))
      (set! (.-receiveShadow light) (:receive-shadow cfg))
      (apply-shadow-settings! light (:shadow cfg))))
  (destroy! [_ _ _ _])
  IUpdateableEntityType
  (update! [_ _ ^three/Light obj cfg]
    (update-fn obj cfg)
    (set! (.-castShadow obj) (:cast-shadow cfg))
    (set! (.-receiveShadow obj) (:receive-shadow cfg))
    (apply-shadow-settings! obj (:shadow cfg))))

(defn- apply-props-clj! [^js obj properties]
  (doseq [[k v] properties]
    (gobject/set obj (name k) v))
  obj)

(def builtin-entity-types
  {;; Common
   :object (reify IEntityType
             (create [_ _ _] (three/Object3D.))
             (destroy! [_ _ _ _])
             IUpdateableEntityType
             (update! [_ _ obj _] obj))
   :group (reify IEntityType
            (create [_ _ _] (three/Group.))
            (destroy! [_ _ _ _])
            IUpdateableEntityType
            (update! [_ _ obj _] obj))
   :instance (reify IEntityType
               (create [_ _ {:keys [object]}]
                 object)
               (destroy! [_ _ _ _]))

   ;; Cameras
   :perspective-camera (reify IEntityType
                         (create [_ _ cfg]
                           (let [cam (three/PerspectiveCamera. 75.0 1.0 0.1 2000.0)]
                             (set! (.-active cam) true)
                             (apply-props-clj! cam cfg)
                             (.updateProjectionMatrix cam)
                             cam))
                         (destroy! [_ _ _ _])
                         IUpdateableEntityType
                         (update! [_ _ ^three/PerspectiveCamera o cfg]
                           (apply-props-clj! o cfg)
                           (.updateProjectionMatrix o)
                           o))
   :orthographic-camera (reify IEntityType
                          (create [_ _ cfg]
                            (let [cam (three/OrthographicCamera. -1 1 1 -1 0.1 2000.0)]
                              (set! (.-active cam) true)
                              (apply-props-clj! cam cfg)
                              (.updateProjectionMatrix cam)
                              cam))
                          (destroy! [_ _ _ _])
                          IUpdateableEntityType
                          (update! [_ _ ^three/OrthographicCamera o cfg]
                            (apply-props-clj! o cfg)
                            (.updateProjectionMatrix o)
                            o))
   ;; Primitives
   :plane (->MeshEntity (fn [{:keys [width height width-segments height-segments]}]
                          (three/PlaneGeometry. (or width 1)
                                                (or height 1)
                                                (or width-segments 1)
                                                (or height-segments 1))))
   :box (->MeshEntity (fn [{:keys [width height depth width-segments height-segments depth-segments]}]
                        (three/BoxGeometry. (or width 1)
                                            (or height 1)
                                            (or depth 1)
                                            (or width-segments 1)
                                            (or height-segments 1)
                                            (or depth-segments 1))))
   :sphere (->MeshEntity (fn [{:keys [radius width-segments height-segments phi-start
                                      phi-length theta-start theta-length]}]
                           (three/SphereGeometry. (or radius 1)
                                                  (or width-segments 8)
                                                  (or height-segments 6)
                                                  (or phi-start 0)
                                                  (or phi-length pi-times-2)
                                                  (or theta-start 0)
                                                  (or theta-length pi))))
   :cylinder (->MeshEntity (fn [{:keys [radius-top radius-bottom height radial-segments
                                        height-segments open-ended? theta-start theta-length]}]
                             (three/CylinderGeometry. (or radius-top 1)
                                                      (or radius-bottom 1)
                                                      (or height 1)
                                                      (or radial-segments 8)
                                                      (or height-segments 1)
                                                      open-ended?
                                                      (or theta-start 0)
                                                      (or theta-length pi-times-2))))
   :circle (->MeshEntity (fn [{:keys [radius segments theta-start theta-length]}]
                           (three/CircleGeometry. (or radius 1)
                                                  (or segments 8)
                                                  (or theta-start 0)
                                                  (or theta-length pi-times-2))))
   :cone (->MeshEntity (fn [{:keys [radius height radial-segments height-segments open-ended?
                                    theta-start theta-length]}]
                         (three/ConeGeometry. (or radius 1)
                                              (or height 1)
                                              (or radial-segments 8)
                                              (or height-segments 1)
                                              open-ended?
                                              (or theta-start 0)
                                              (or theta-length pi-times-2))))
   :dodecahedron (->MeshEntity (fn [{:keys [radius detail]}]
                                 (three/DodecahedronGeometry. (or radius 1)
                                                              (or detail 0))))
   :icosahedron (->MeshEntity (fn [{:keys [radius detail]}]
                                (three/IcosahedronGeometry. (or radius 1)
                                                            (or detail 0))))
   :octahedron (->MeshEntity (fn [{:keys [radius detail]}]
                               (three/OctahedronGeometry. (or radius 1)
                                                          (or detail 0))))
   :tetrahedron (->MeshEntity (fn [{:keys [radius detail]}]
                                (three/TetrahedronGeometry. (or radius 1)
                                                            (or detail 0))))
   :ring (->MeshEntity (fn [{:keys [inner-radius outer-radius theta-segments phi-segments
                                    theta-start theta-length]}]
                         (three/RingGeometry. (or inner-radius 0.5)
                                              (or outer-radius 1.0)
                                              (or theta-segments 8)
                                              (or phi-segments 8)
                                              (or theta-start 0)
                                              (or theta-length pi-times-2))))
   :torus (->MeshEntity (fn [{:keys [radius tube radial-segments tubular-segments arc]}]
                          (three/TorusGeometry. (or radius 1)
                                                (or tube 0.4)
                                                (or radial-segments 8)
                                                (or tubular-segments 6)
                                                (or arc pi-times-2))))
   :torus-knot (->MeshEntity (fn [{:keys [radius tube tubular-segments radial-segments p q]}]
                               (three/TorusKnotGeometry. (or radius 1)
                                                         (or tube 0.4)
                                                         (or tubular-segments 64)
                                                         (or radial-segments 8)
                                                         (or p 2)
                                                         (or q 3))))
   :shape (->MeshEntity (fn [{:keys [shape]}]
                          (three/ShapeGeometry. shape)))
   ;; Lights
   :ambient-light (->LightEntity
                   (fn [{:keys [intensity color]}]
                     (three/AmbientLight. (or color 0xFFFFFF)
                                          (or intensity 1.0)))
                   (fn [^three/Light o cfg]
                     (apply-props-clj! o cfg)
                     o))
   :point-light (->LightEntity
                 (fn [{:keys [intensity color distance decay]}]
                   (three/PointLight. (or color 0xFFFFFF)
                                      (or intensity 1.0)
                                      (or distance 0)
                                      (or decay 1.0)))
                 (fn [^three/Light o cfg]
                   (apply-props-clj! o cfg)
                   o))
   :hemisphere-light (->LightEntity
                      (fn [{:keys [sky-color ground-color intensity]}]
                        (three/HemisphereLight.
                         (or sky-color 0xFFFFFF)
                         (or ground-color 0xFFFFFF)
                         (or intensity 1)))
                      (fn [^three/Light o cfg]
                        (apply-props-clj! o (rename-keys cfg {:sky-color :skyColor
                                                              :ground-color :groundColor}))
                        o))
   :directional-light (->LightEntity
                       (fn [{:keys [intensity color target]}]
                         (let [light (three/DirectionalLight. (or color 0xFFFFFF)
                                                              (or intensity 1.0))]
                           (set! (.-originalTarget light) (.-target light))
                           (when target
                             (set! (.-target light) target))
                           light))
                       (fn [^three/Light o {:keys [target] :as cfg}]
                         (if target
                           (set! (.-target o) target)
                           (set! (.-target o) (.-originalTarget o)))
                         (apply-props-clj! o (dissoc cfg :target))
                         o))
   :rect-area-light (->LightEntity
                     (fn [{:keys [intensity color width height]}]
                       (three/RectAreaLight. (or color 0xFFFFFF)
                                             (or intensity 1.0)
                                             (or width 10)
                                             (or height 10)))
                     (fn [^three/Light o cfg]
                       (apply-props-clj! o cfg)
                       o))
   :spot-light (->LightEntity
                (fn [{:keys [intensity color distance angle penumbra decay target]}]
                  (let [light (three/SpotLight. (or color 0xFFFFFF)
                                                (or intensity 1.0)
                                                (or distance 0)
                                                (or angle pi-over-2)
                                                (or penumbra 0)
                                                (or decay 1.0))]
                    (set! (.-originalTarget light) (.-target light))
                    (when target
                      (set! (.-target light) target))
                    light))
                (fn [^three/Light o {:keys [target] :as cfg}]
                  (if target
                    (set! (.-target o) target)
                    (set! (.-target o) (.-originalTarget o)))
                  (apply-props-clj! o (dissoc cfg :target))
                  o))})
                                        
                                  
