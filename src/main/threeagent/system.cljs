(ns threeagent.system)

(defprotocol ISystem
  (init [this threeagent-context]
    "
     Called after threeagent initialization as part of `threeagent.core/render`
     `threeagent-context`: A map with the context for the threeagent instance: 
     ```clojure
      {:threejs-renderer <ThreeJS Renderer Instance>
       :threejs-scene <ThreeJS Scene>
       :systems <Map of all systems>
       :canvas <Canvas DOM Element>}

     If the returned value is a function, it will be invoked immediately after all remaining
     `ISystem/init` have initialized. Otherwise, the returned value is ignored.
     ```
    ")
  (destroy [this threeagent-context]
    "
     Called immediately before the threeagent context is destroyed.
     This usually happens when `threeagent.core/render` is re-executed as part of a hot-reload.
     `threeagent-context`: A map with the context for the threeagent instance: 
     ```clojure
      {:threejs-renderer <ThreeJS Renderer Instance>
       :threejs-scene <ThreeJS Scene>
       :systems <Map of all systems>
       :canvas <Canvas DOM Element>}

     If the returned value is a function, it will be invoked immediately after all remaining
     `ISystem/destroy` have been invoked. Otherwise, the returned value is ignored.
     ```
    ")
  (on-entity-added [this id ^js threejs-obj config]
    "
     Called when a new entity is added to the scene.
     `id`: The ID of this entity, when specified via the `:id` property, `nil` otherwise
     `threejs-obj`: the ThreeJS object instance for this entity 
     `config`: this entity's configuration value for this system's key

     If the returned value is a function, it will be invoked after all remaining
     `ISystem/on-entity-added` have been invoked for this entity and it's children. 
     Otherwise, the returned value is ignored.
    ")
  (on-entity-removed [this id ^js threejs-obj config]
    "
     Called when an entity is removed from the scene
     `id`: The ID of this entity, when specified via the `:id` property, `nil` otherwise
     `threejs-obj`: the ThreeJS object instance for this entity 
     `config`: this entity's configuration value for this system's key

     If the returned value is a function, it will be invoked after all remaining
     `ISystem/on-entity-removed` have been invoked for this entity and it's children.
     Otherwise, the returned value is ignored.
    ")
  (tick [this delta-time]
    "
     Called each frame
     `delta-time`: The elapsed time (in seconds) since the last tick (0 for the initial tick)
    "))
