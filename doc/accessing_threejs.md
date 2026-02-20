# Accessing ThreeJS Objects

Often times, we'll want to access the actual ThreeJS objects that Threeagent is managing, so that
we can leverage ThreeJS functionality. For instance, we might want to use ThreeJS's raycaster to
pick objects in our scene, or use a physics simulation to directly drive the transformation of each object.

Threeagent provides a few ways to get access to the ThreeJS substrate.

## Render Context

The `threeagent.core/render` function returns a context map containing the core ThreeJS objects:

```clojure
(let [ctx (th/render root-fn (.getElementById js/document "root"))]
  (:threejs-renderer ctx)        ; WebGPURenderer instance
  (:threejs-scene ctx)           ; Scene instance (primary scene)
  (:threejs-default-camera ctx)  ; Default PerspectiveCamera (primary scene)
  (:canvas ctx)                  ; Canvas element
  (:threejs-scenes ctx)          ; Map of {scene-key -> Scene} for all scenes
  (:scene-cameras ctx)           ; Map of {scene-key -> Camera} for all scenes
  (:threejs-render-pipeline ctx) ; RenderPipeline instance (if :render-pipeline provided)
  (:frame-pacer ctx))            ; Frame pacer (if :auto-frame-pacing enabled)
```

The renderer is a `WebGPURenderer` from `three/webgpu`, which automatically falls back to WebGL 2 on browsers without WebGPU support.

When using [multi-scene rendering](./multi_scene.md), `:threejs-scene` and `:threejs-default-camera` refer to the primary scene (the first in `:render-order`). Use `:threejs-scenes` and `:scene-cameras` to access individual scenes by key.

## Lifecycle Hooks

We can set the `:on-added` and `:on-removed` of any entity to a callback function that will be invoked with
the ThreeJS object for that entity.  The `:on-added` callback will be invoked after the object is first
added to the scene graph. The `:on-removed` callback will be invoked after the object is removed from the
scene graph.

There is also `:ref` which is an alias for `:on-added`.

## Entity Registry

The `:entity-registry` option allows you to maintain a map of entity IDs to their ThreeJS objects. Pass an
atom to the `render` function, and Threeagent will automatically populate it with entities that have an `:id`
property defined.

```clojure
(def registry (atom {}))

(th/render
  root-fn
  (.getElementById js/document "root")
  {:entity-registry registry})
```

Now entities with an `:id` property will be registered:

```clojure
(defn my-scene []
  [:object
    [:box {:id :player-box :position [0 1 0]}]
    [:sphere {:id :enemy-sphere :position [5 0 0]}]])
```

You can then access the ThreeJS objects directly by their ID:

```clojure
(let [player-obj (get @registry :player-box)
      enemy-obj (get @registry :enemy-sphere)]
  ;; Use ThreeJS objects directly
  (.lookAt player-obj (.-position enemy-obj)))
```

This is useful for imperative operations, integrating with physics engines, or any case where you need
direct access to specific ThreeJS objects by name.

## Extending Threeagent

For more advanced use cases, we can define custom `ISystem` and `IEntityType` implementations to create
and manage ThreeJS objects. See [Extending Threeagent](./extending_threeagent.md) for more information.
