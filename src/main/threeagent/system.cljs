(ns threeagent.system)

(defprotocol ISystem
  (on-entity-added [this key ^js threejs-obj config]
    "
     Called when a new entity is added to the scene.
     `key`: The scene-wide unique key for this entity
     `threejs-obj`: the ThreeJS object instance for this entity 
     `config`: this entity's configuration value for this system's key
    ")
  (on-entity-removed [this key ^js threejs-obj config]
    "
     Called when an entity is removed from the scene
     `key`: The scene-wide unique key for this entity
     `threejs-obj`: the ThreeJS object instance for this entity 
     `config`: this entity's configuration value for this system's key
    ")
  (tick [this delta-time]
    "
     Called each frame
     `delta-time`: The elapsed time (in seconds) since the last tick (0 for the initial tick)
    "))
