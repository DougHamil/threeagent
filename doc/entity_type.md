# Entity Types

In Threeagent, each entity in our scene graph is defined with a given entity-type. The entity-type
is determined by the leading keyword in the entity's vector:
```clojure
[:object
  [:box]
  [:sphere]]
```

In this example, we have three entities in our scene, each with a different type: `:object`, `:box`, and `:sphere`.

The entity-type determines the behavior of the entity, which can be further customized by the entity-type specific
properties. For instance, we can define a `:radius` for any entity of type `:sphere`:
```clojure
[:object
  [:sphere {:radius 5.2}]]
```


## Built-in Entity Types

For convenience, Threeagent has [some entity-types built-in](./built_in_entity_types.md) and we can make use of them immediately.
These built-in types will generally correspond to the built-in object types in ThreeJS. For instance, we
can make use of the different types of lights provided by ThreeJS:

```clojure
[:object
  [:point-light {:intensity 1.2
                 :decay 0.5}]
  [:spot-light {:color "red"
                :penumbra 0.2}]]
```

