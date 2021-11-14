# Getting Started

Follow this guide to get up and running with your first Threeagent 3D app.

## Hiccup-like Syntax

Just as hiccup is used to define your DOM tree in Reagent, Threeagent uses a hiccup-like syntax to define the Three.js objects in your Three.js scene graph.

For example, this is how we would create two shapes with a common parent:
```clojure

[:object
  [:sphere]
  [:box]]

```

`:object`, `:sphere`, and `:box` are all built-in [entity-types](./entity_type.md) provided by Threeagent. `:object` is a simple ThreeJS Object3D instance, without any geometry or visual behavior. It is commonly used as a parent to group together sub-objects. `:sphere` and `:box` represent ThreeJS objects with a SphereGeometry and BoxGeometry, respectively.

### Position, Rotation, and Scale

We can add a map after the keyword of the vector to define the properties of that particular entity, just as you would in hiccup. We use this map to define the 3D transformations for any entity:

```clojure
[:object
  [:sphere {:position [0 0 -10]
            :rotation [(/ PI 2.0) 0 0]}]
  [:box {:scale [2 4 2.5]}]]
```

We can define the `:position`, `:rotation`, and `:scale` properties of any entity in our scene in this manner. These properties should always be defined as a vector of length 3, which will correspond to the `XYZ` values.

The `:rotation` vector should be defined in radians and will be interpreted as XYZ euler angles.

Our 3D transformations are always defined in local-space, relative to the parent object. This is one of the primary conveniences of the scene-graph representation, as ThreeJS will automatically determine the world-space transformation of all objects in our scene.

```clojure
[:object
  [:box {:position [2 0 0]} # [2, 0, 0] in world-space
    [:box {:position [3 1 0]}]]] # [5, 1, 0] in world-space
```

### Entity-Specific Properties

We not only use the entity property map to define its transformations, we also use it to define entity-specific properties:
```clojure
[:object
  [:box {:width 5}]
  [:sphere {:radius 0.5}]]
```

The properties we can use are dependent the type of the entity. In this case the `:sphere` has a `:radius` property, while the `:box` has a `:width` property.

If we define a property that is not supported by that entity-type, it will be ignored.

### Component Functions

Now that we can define our scene-graph using this hiccup-like syntax, we want to build components that we can re-use across our scene-graph. 
Again, just like Reagent, we use normal Clojure functions to define our re-usable components. Our functions just need to return the hiccup-structure that represents our component:
```clojure

(defn stack-of-boxes [height]
  [:object
    (for [i (range height)]
      [:box {:position [0 0 i]}])])
      
      
(defn scene-root []
  [:object
    [:sphere]
    [stack-of-boxes 5]])
```

Notice that we use square brackets to refer to our component function: `[stack-of-boxes 5]`. We do this for the same [reason as Reagent](https://cljdoc.org/d/reagent/reagent/1.1.0/doc/tutorials/using-square-brackets-instead-of-parentheses-).

Our example will create a stack of boxes as a sibling node to our `:sphere` defined in the root component.

### State and Reactive Rendering

At this point, we have a completely static scene. Let's fix this by adding some state into the mix and leveraging
the real power of Threeagent: reactive re-rendering.

```clojure
(ns my-app
  (:require [threeagent.core :as th]))

(defonce number-of-boxes (th/atom 1))

(defn stack-of-boxes [height]
  [:object
    (for [i (range height)]
      [:box {:position [0 0 i]}])])
      
      
(defn scene-root []
  [:object
    [:sphere]
    [stack-of-boxes @number-of-boxes]])
```


We start with defining a [reactive-atom](https://cljdoc.org/d/reagent/reagent/1.1.0/doc/tutorials/-wip-managing-state-atoms-cursors-reactions-and-tracking) called `number-of-boxes` which stores the number of boxes we want in our stack.

Now, if we ever mutate our `number-of-boxes` atom, Threeagent will automatically re-render our `scene-root` component, as it knows that it depends on the state stored in our `number-of-boxes` atom.


While we use the `threeagent.core/atom` function to create the atom, this atom is actually a Reagent reactive-atom. This means we can use all of the normal Reagent functions to interact with it. Even better, it means we can share our application state between Threeagent and Reagent. So, we could create a 2D interface in HTML via Reagent, and a 3D scene in ThreeJS via Threeagent!

## Entities

Each element in our hiccup-defined scene-graph is called an `entity`. Each entity is of a specific [entity-type](./extending_threeagent.md) and has a map of properties defining its behavior. Ultimately, a Threeagent entity corresponds to a ThreeJS object instance.

There are 2 lifecycle events for entities: 
* `added` occurs when Threeagent adds this entity to the ThreeJS scene graph
* `removed` occurs when Threeagent removes this entity from the ThreeJS scene graph

We can respond to these lifecycle events to add custom behavior and access the underlying ThreeJS object of
an entity. See [Extending Threeagent](./extending_threeagent.md) for more information.

Additionally, we can set the `:id` property of an entity to specify our own identifiers for an entity. This
has no importance to Threeagent, but it will be passed along to the `ISystem` and `IEntityType` lifecycle event methods. This can be a useful way to keep track of entities, or to demark special entities in our scene.
For example:

```clojure
[:object {:id "world"}
  [:object {:id "player"}]
  [:object {:id "zombie"}]
  [:object {:id "zombie"}]] ;; ID conflict!
```

Note that Threeagent does _not_ check or enforce that IDs are unique across entities. While ID conflicts won't
affect Threeagent's operation, it would be problematic if we are using entity-ids to track entities in our global state.
