(ns threeagent.impl.component
  (:require [threeagent.impl.threejs :as threejs]
            ["three" :as three])
  (:require-macros [threeagent.impl.component-macros :refer [defrenderer]]))

(def ^:private ^:dynamic component-registry {})

(defn register-component-renderer! [key render-fn]
  (set! component-registry (assoc component-registry key render-fn)))

(defn render-component [key config]
  (let [renderer (get component-registry key)]
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
(defrenderer :object [c] (threejs/object))
(defrenderer :group [c] (threejs/group))
(defrenderer :camera [{:keys [fov aspectRatio near far]}]
  (threejs/perspective-camera fov aspectRatio near far))
(defrenderer :instance [{:keys [object]}] object)

;; Primitives
(defrenderer :plane [{:keys [dims segment-dims material]}]
  (let [[w h] dims
        [sw sh] segment-dims
        geo (threejs/plane-geometry w h sw sh)]
    (to-mesh geo material)))

(defrenderer :box [{:keys [dims material]}]
  (let [[x y z] dims
        geo (threejs/box-geometry x y z)]
    (to-mesh geo material)))
    
(defrenderer :sphere [{:keys [radius width-segments height-segments material]}]
  (let [geo (threejs/sphere-geometry radius
                                     (or width-segments 12)
                                     (or height-segments 12))]
    (to-mesh geo material)))

;; Lights
(defrenderer :point-light [{:keys [color intensity distance]}]
  (threejs/point-light color intensity distance))

(defrenderer :hemisphere-light [{:keys [sky-color ground-color intensity]}]
  (threejs/hemisphere-light sky-color ground-color intensity))

(defrenderer :directional-light [{:keys [color intensity]}]
  (threejs/directional-light color intensity))

;; Text
(defrenderer :text [{:keys [text material] :as cfg}]
  (let [geo (threejs/text-geometry text cfg)]
    (to-mesh geo material)))

