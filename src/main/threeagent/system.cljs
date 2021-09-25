(ns threeagent.system)

(defprotocol ISystem
  (init [this threeagent-config]
    "
     Called after threeagent initialization as part of `threeagent.core/render`
     `threeagent-config`: A map with the configuration for the threeagent instance: 
     ```clojure
      {:threejs-renderer <ThreeJS Renderer Instance>
       :threejs-scene <ThreeJS Scene>
       :systems <Map of all systems>
       :canvas <Canvas DOM Element>}

     If the returned value is a function, it will be invoked immediately after all remaining
     `ISystem/init` have initialized. Otherwise, the returned value is ignored.
     ```
    ")
  (destroy [this threeagent-config]
    "
     Called immediately before the threeagent context is destroyed.
     This usually happens when `threeagent.core/render` is re-executed as part of a hot-reload.
     `threeagent-config`: A map with the configuration for the threeagent instance: 
     ```clojure
      {:threejs-renderer <ThreeJS Renderer Instance>
       :threejs-scene <ThreeJS Scene>
       :systems <Map of all systems>
       :canvas <Canvas DOM Element>}

     If the returned value is a function, it will be invoked immediately after all remaining
     `ISystem/destroy` have been invoked. Otherwise, the returned value is ignored.
     ```
    ")
  (on-entity-added [this entity-context entity-id threejs-obj system-config]
    "
     Called when a new entity is added to the scene.
     `entity-context`: A map representing the inherited context for this entity
     `entity-id`: The ID of this entity, when specified via the `:id` property, `nil` otherwise
     `threejs-obj`: the ThreeJS object instance for this entity 
     `system-config`: this entity's configuration value for this system's key

     If the returned value is a function, it will be invoked after all remaining
     `ISystem/on-entity-added` have been invoked for this entity and it's children. 
     Otherwise, the returned value is ignored.
    ")
  (on-entity-removed [this entity-context entity-id threejs-obj system-config]
    "
     Called when an entity is removed from the scene
     `entity-context`: A map representing the inherited context for this entity
     `entity-id`: The ID of this entity, when specified via the `:id` property, `nil` otherwise
     `threejs-obj`: the ThreeJS object instance for this entity 
     `system-config`: this entity's configuration value for this system's key

     If the returned value is a function, it will be invoked after all remaining
     `ISystem/on-entity-removed` have been invoked for this entity and it's children.
     Otherwise, the returned value is ignored.
    ")
  (tick [this delta-time]
    "
     Called each frame
     `delta-time`: The elapsed time (in seconds) since the last tick (0 for the initial tick)
    "))
