(ns threeagent.impl.component
  (:require [threeagent.impl.threejs :as threejs]
            [threeagent.alpha.component :as component]
            ["three" :as three])
  (:require-macros [threeagent.alpha.macros :refer [defcomponent]]))

(defn render-component [key config]
  (let [renderer (get component/*registry* key)]
    (if renderer
      (renderer config)
      (println "Missing renderer for object type" key))))

(defn- ->material [config]
  (if (instance? three/Material config)
    config
    (threejs/mesh-phong-material config)))

(defn- to-mesh [geo material-config]
  (let [mat (->material material-config)]
    (threejs/mesh geo mat)))

;; Basic
(defcomponent :object [c] (threejs/object))
(defcomponent :group [c] (threejs/group))
(defcomponent :camera [{:keys [fov aspectRatio near far]}]
  (threejs/perspective-camera fov aspectRatio near far))
(defcomponent :instance [{:keys [object]}] object)

;; Primitives
(defcomponent :plane [{:keys [dims segment-dims material]}]
  (let [[w h] dims
        [sw sh] segment-dims
        geo (threejs/plane-geometry w h sw sh)]
    (to-mesh geo material)))

(defcomponent :box [{:keys [dims material]}]
  (let [[x y z] dims
        geo (threejs/box-geometry x y z)]
    (to-mesh geo material)))
    
(defcomponent :sphere [{:keys [radius width-segments height-segments material]}]
  (let [geo (threejs/sphere-geometry radius
                                     (or width-segments 12)
                                     (or height-segments 12))]
    (to-mesh geo material)))

;; Lights
(defcomponent :point-light [{:keys [color intensity distance]}]
  (threejs/point-light color intensity distance))

(defcomponent :hemisphere-light [{:keys [sky-color ground-color intensity]}]
  (threejs/hemisphere-light sky-color ground-color intensity))

(defcomponent :directional-light [{:keys [color intensity]}]
  (threejs/directional-light color intensity))

;; Text
(defcomponent :text [{:keys [text material] :as cfg}]
  (let [geo (threejs/text-geometry text cfg)]
    (to-mesh geo material)))

