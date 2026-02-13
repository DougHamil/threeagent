(ns threeagent.impl.types
  (:require [threeagent.impl.virtual-scene :as vscene]))

(deftype Context [^vscene/Scene virtualScene
                  ^js sceneRoot
                  ^js domRoot
                  ^js frameInterval
                  ^js canvas
                  ^js camera
                  ^js cameras
                  ^js clock
                  ^js renderer
                  ^js beforeRenderCb
                  ^js afterRenderCb
                  entityTypes
                  systems
                  ^js defaultCamera
                  entityRegistry]
  Object)
