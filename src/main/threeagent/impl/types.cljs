(ns threeagent.impl.types
  (:require [threeagent.impl.virtual-scene :as vscene]))

(deftype SceneContext [^js key
                       ^vscene/Scene virtualScene
                       ^js sceneRoot
                       ^js camera
                       ^js cameras
                       ^js defaultCamera
                       entityRegistry]
  Object)

(deftype Context [^js domRoot
                  ^js frameInterval
                  ^js canvas
                  ^js clock
                  ^js renderer
                  ^js beforeRenderCb
                  ^js afterRenderCb
                  entityTypes
                  systems
                  scenes
                  renderOrder
                  ^js activeSceneCtx
                  ^js primarySceneKey]
  Object)
