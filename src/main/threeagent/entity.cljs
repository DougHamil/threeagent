(ns threeagent.entity
  (:require ["three" :as three]))

(defprotocol IEntity
  (create [this config]
    "Returns a new instance of this entity type, based on the provided `config`.
     The returned instance should be a ThreeJS `Object3D` or one of its sub-classes.")
  (destroy! [this ^three/Object3D object]
    "Destroys an existing instance of this entity type."))

(defprotocol IUpdateableEntity
  (update! [this ^three/Object3D object new-config]
    "Updates an existing instance of this entity entity type in-place"))
