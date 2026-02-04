# Extending Threeagent

## Custom Entity Types

As we continue to add functionality to our Threeagent application, we'll likely find the built-in entity-types too
limiting. We'd like to extend Threeagent with our own entity-types to make it easier to build our application.

For example, let's say we want to add custom 3D models to our scene. Our models are stored in [GLTF](https://docs.fileformat.com/3d/gltf/) and we'd like to add them to our scene just like any other entity-type:

```clojure

(defn player-character []
  [:object
    [:box]
    [:model {:path "/assets/models/player_character.gltf"}]])
```

So, let's add support for our custom entity-type `:model`. We start by implementing the `IEntityType` protocol:
```clojure
(ns my-app.model
  (:require [threeagent.entity :refer [IEntityType]]
            ["three/webgpu" :as three]))
            
(defn- load-model [path] 
  ;; Returns a Promise with the loaded model...
  )
  
(deftype ModelEntity []
  IEntityType
  (create [this context {:keys [path] :as config}]
    (let [parent (three/Object3D.)]
      (-> (load-model path)
          (.then (fn [model]
                   (.add parent model))))
      parent))
  (destroy! [this context config object]
   ;; No clean-up required
   ))
```

The `IEntityType` protocol defines two methods:
1. The `create` method is called when Threeagent needs to add a new instance of this entity-type to our scene. It should return a ThreeJS object (that is, an instance of any subtype of `Object3D`). Threeagent will then add this object to the scene-graph at the correct location. Note that we do _not_ need to set any of the 3D transformations for our created object, Threeagent will handle that.
2. The `destroy!` method is called when Threeagent is removing an entity of our entity-type. We use this method to do any clean-up necessary for our EntityType. Note that we do _not_ need to remove the object from the scene-graph ourselves, Threeagent will do this for us.

Assuming our `load-model` function handles the fetching and loading of our GLTF model, our `IEntityType` is pretty straightforward:
1. The `create` method creates an empty Object3D instance, kicks off the `load-model` promise,
then returns the empty `parent` instance. As soon as the `load-model` promise is resolved, the loaded model is parented to the `parent` instance.
2. The `destroy!` method doesn't do anything because we don't have any resources we need to clean-up manually.

---

>This implementation of our `ModelEnity` is not ideal for a few reasons:
>1. It loads the model asynchronously, meaning the model will appear some time after a given `:model` entity is added to the scene
>2. The model will be fetched and loaded each time a `:model` entity is added to the scene
>
>A better implementation would pre-fetch and populate a pool of model instances, which we'd claim and return from in our `create` and `destroy!` methods, respectively. We'll leave this as an exercise for the reader.

---

With our `ModelEntity` type defined, we need to tell Threeagent to use it. To do so, we use the `:entity-types` setting when calling the `threeagent.core/render` fn. This should be a map of keywords to `IEntityType` instances:
```clojure
(th/render
  root-fn 
  canvas
  {:entity-types {:model (->ModelEntity)}})
```

Threeagent will now call `ModelEntity/create` and `ModelEntity/destroy!` when it finds an entity of type `:model`.

## Custom Systems

Threeagent loosely follows the [Entity-Component-System pattern](https://en.wikipedia.org/wiki/Entity_component_system), which allows us to add new functionality across all entity-types by leveraging the `ISystem` protocol.

Let's say we wanted to make our 3D objects clickable and invoke a callback function any time the object is clicked. This would be similiar to using the `:on-click` handler on Reagent DOM elements, but in this case we need to deal with how to pick the object in the scene from the mouse position and invoke our handler when the click occurs. Additionally, we'd like for this click handler to work across any entity-type: `:sphere`, `:box`, or our custom `:model` types should all be clickable.

To do this, we'll start with defining an implementation of the `ISystem` protocol:
```clojure
(ns my-app.input-system
  (:require [threeagent.system :refer [ISystem]]))
  
(defn- handle-clicks [canvas local-state]
  ;; 1. Register a 'click' event handler on the canvas
  ;; 2. When click occurs, use ThreeJS Raycaster to pick a 3D object
  ;; 3. If an object is picked, invoke the :on-click handler in our local-state, if defined
  )
  
(deftype InputSystem [local-state]
  ISystem
  (init [this context]
    ;; Register `click` event handler on the canvas DOM element
    ;; and pick the underlying 3D object
    (handler-clicks (:canvas local-state) local-state)
  (destroy [_ context]
    ;; Unused
    )
  (on-entity-added [this ctx entity-id obj {:keys [on-click]}]
    ;; Store the on-click callback fn for this entity
    (swap! local-state assoc-in [:handlers entity-id] on-click))
  (on-entity-updated [this ctx entity-id obj {:keys [on-click]}]
    ;; Update the on-click callback fn when config changes
    (swap! local-state assoc-in [:handlers entity-id] on-click))
  (on-entity-removed [this ctx entity-id obj config]
    (swap! local-state assoc-in [:handlers entity-id] nil))
  (tick [this delta-time]
    ;; Unused
    ))
```

The `ISystem` protocol defines 6 different methods:
1. The `init` method is called when Threeagent is initializing (during the `threeagent.core/render` call).
  It can be used to setup any state needed by the system.
2. The `destroy` method is called when Threeagent is shutting down. It can be used to cleanup any state.
3. The `on-entity-added` method is invoked whenever a new entity is added to the scene that has our system's
property defined. We can access the entity's ThreeJS object, the Threeagent context, and the properties
defined on this entity for our system.
4. The `on-entity-updated` method is invoked whenever an entity's configuration changes and the entity type
supports in-place updates (implements `IUpdateableEntityType`). This allows systems to respond to configuration
changes without the entity being fully removed and re-added. The method receives the same arguments as
`on-entity-added`.
5. The `on-entity-removed` method is invoked whenever Threeagent is removing an entity from the scene. We can
use this to clean up any state associated with this particular entity.
6. The `tick` method is called every frame with the time (in seconds) since the last frame was rendered. This can be used by systems that need to run some logic every frame, such as a physics system.

> NOTE: Threeagent does not support the ordering of `ISystem` invocations. Meaning, we cannot control which
> system will have its `on-entity-added` method called before another, in the case where an entity has
> multiple system components defined.


With our `InputSystem` defined, we need to provide an instance of it to Threeagent. Similar to the entity-types, we do this by setting the `:systems` property to a map of keyword to system instance:
```clojure
(th/render 
  root-fn
  canvas
  {:systems {:input (->InputSystem (atom {}))}})
```

Now, whenever we add a `:input` property to _any_ entity, our `InputSystem` will be invoked:
```clojure
[:object {:input {:on-click #(println "Click 1")}}
  [:box {:input {:on-click #(println "Click 2")}}
    [:sphere {:input {:on-click #(println "Click 3")}}]]]
```

Defining custom `ISystem` implementations can be a great way to add new functionality to our application.
They can be particularly helpful if we are making a game; we could have a `PhysicsSystem`, `ParticleSystem`,
`AudioSystem`, `CombatSystem`, etc.

### Lifecycle Event Ordering

We should note the ordering of a system's `on-entity-added` and `on-entity-removed` method calls.
Let's say we have a scene with this structure:

```clojure
[:a {:input {}}
  [:b {:input {}}]]
```

When Threeagent is adding the `:a` and `:b` entities to our scene, it will invoke the `on-entity-added` method of our `InputSystem` in _outside-in_, _depth-first_ order. That is, `on-entity-added` will first be called for the `:a` entity, followed by the `:b` entity.

In some advanced use-cases, this might cause us some pain. Perhaps we require the children of a given entity to be initialized first. We can do this by returning a function from `on-entity-added` or `on-entity-removed`. This function will be invoked _after_ the children have been initialized.

For example, we might have a system that calculates the bounding-box of an entity, and this bounding-box should contain all of the bounding-boxes of the child entities. By returning a function, we can delay the bounding-box calculation until after the children have been calculated:

```clojure
(deftype BoundingBoxSystem []
  ISystem
  ...
  (on-entity-added [_ _ _ obj cfg]
   (fn []
     (set!
        (.-boundingBox obj) 
        (calculate-bounding-box obj (.-children obj)))))
  ...
  )
```
