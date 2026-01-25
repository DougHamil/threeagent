(ns threeagent.impl.threejs
  (:refer-clojure :exclude [get-in])
  (:require ["three/webgpu" :as three]))


(defn mesh-phong-material [c]
  (new three/MeshPhongMaterial (clj->js c)))

(defn euler [x y z]
  (new three/Euler x y z "XYZ"))

(defn set-position! [^js obj [x y z]]
  (.set (.-position obj) x y z)
  obj)

(defn set-rotation! [^js obj [x y z]]
  (.setRotationFromEuler obj (euler x y z))
  obj)

(defn set-scale! [^js obj [x y z]]
  (.set (.-scale obj) x y z)
  obj)
  
(defn set-cast-shadow! [^js obj ?true]
  (set! (.-castShadow obj) ?true)
  obj)
  
(defn set-receive-shadow! [^js obj ?true]
  (set! (.-receiveShadow obj) ?true)
  obj)
  
(defn add-child! [^js parent ^js child] (.add parent child))

(defn remove-child! [^js parent ^js child] (.remove parent child))

(defn remove-all-children! [^js parent]
  (for [c (.-children parent)]
    (remove-child! parent c))
  parent)

(defn get-in [^three/Object3D parent path]
  (if (seq path)
    (let [next (first path)]
      (if (string? next)
        (recur (.getObjectByName parent next) (rest path))
        (if (= :.. next)
          (recur (.-parent parent) (rest path))
          (recur (aget (.-children parent) next) (rest path)))))
    parent))
