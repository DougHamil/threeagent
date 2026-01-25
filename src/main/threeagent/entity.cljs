(ns threeagent.entity
  (:require ["three/webgpu" :as three]))

(defprotocol IEntityType
  (create [this context entity-config]
    "Returns a new instance of this entity type, based on the provided `entity-config` and `context`.
     The returned instance should be a ThreeJS `Object3D` or one of its sub-classes.")
  (destroy! [this context ^three/Object3D object entity-config]
    "Destroys an existing instance of this entity type."))

(defprotocol IUpdateableEntityType
  (update! [this context ^three/Object3D object new-entity-config]
    "Updates an existing instance of this entity entity type in-place, based on the provided
    `new-entity-config` and `context`."))
