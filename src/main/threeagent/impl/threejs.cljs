(ns threeagent.impl.threejs
  (:require [threeagent.impl.util :refer [$ $!]]))

(defn perspective-camera [fov aspect near far]
  (new js/THREE.PerspectiveCamera fov aspect near far))

(defn point-light [c i d]
  (new js/THREE.PointLight c i d))

(defn directional-light [c i]
  (new js/THREE.DirectionalLight c i))

(defn hemisphere-light [sky-color ground-color intensity]
  (new js/THREE.HemisphereLight sky-color ground-color intensity))

(defn box-geometry [x y z]
  (new js/THREE.BoxGeometry x y z))

(defn plane-geometry [width height segments-width segments-height]
  (new js/THREE.PlaneGeometry width height segments-width segments-height))

(defn sphere-geometry [radius width-seg height-seg]
  (new js/THREE.SphereGeometry radius width-seg height-seg))

(defn text-geometry [text cfg]
  (new js/THREE.TextGeometry text (clj->js cfg)))

(defn mesh-basic-material [c]
  (new js/THREE.MeshBasicMaterial (clj->js c)))

(defn mesh-phong-material [c]
  (new js/THREE.MeshPhongMaterial (clj->js c)))

(defn mesh [geo mat]
  (new js/THREE.Mesh geo mat))

(defn object []
  (new js/THREE.Object3D))

(defn group []
  (new js/THREE.Group))

(defn vec3
  ([[x y z]] (new js/THREE.Vector3 x y z))
  ([x y z] (new js/THREE.Vector3 x y z))
  ([] (new js/THREE.Vector3)))

(defn arrow-helper [dir origin length]
  (new js/THREE.ArrowHelper (vec3 dir) (vec3 origin) length))

(defn vec3->vector [v] [(.-x v) (.-y v) (.-z v)])

(defn euler [x y z]
  (new js/THREE.Euler x y z "XYZ"))

(defn euler->vector [e]
  [(.-x e) (.-y e) (.-z e)])

(defn color
  ([s] (new js/THREE.Color s))
  ([r g b] (new js/THREE.Color r g b)))

(defn raycaster [origin dir near far]
  (new js/THREE.Raycaster origin dir near far))

(defn world-position [obj]
  (when obj
    (let [v (vec3)]
      (.getWorldPosition obj v)
      v)))

(defn rotation->direction [obj]
  (let [quat (.setFromRotationMatrix (new js/THREE.Quaternion) (.-matrixWorld obj))
        vector (vec3 0 1 0)]
    (.applyQuaternion vector quat)
    (.normalize vector)
    vector))

(defn set-position! [obj [x y z]]
  (.set (.-position obj) x y z)
  obj)

(defn set-rotation! [obj [x y z]]
  (.setRotationFromEuler obj (euler x y z))
  obj)

(defn set-scale! [obj [x y z]]
  (.set (.-scale obj) x y z)
  obj)

(defn add-child! [parent child] (.add parent child))

(defn remove-child! [parent child] (.remove parent child))

(defn remove-all-children! [parent]
  (for [c (.-children parent)]
    (remove-child! parent c))
  parent)
