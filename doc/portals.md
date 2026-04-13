# Portals

In Threeagent, we can use something called a _portal_ to traverse a specific ThreeJS object's tree and then
continue extending it from that position. 

Portals are defined using a special keyword, `:>` followed by a vector that represents the navigation path:

```clojure
[:instance {:object some-threejs-object}
  [:> ["Child-1" "Child-1-1" "Child-1-1-2"]
    [:box]]]
```

This can be useful when you are dealing with ThreeJS objects that were not created in our Threeagent scene-graph. Most commonly, this happens when we load a 3D model file into ThreeJS, as we wll get back a tree of ThreeJS
objects that represents the model.

For example, let's say we have a GLTF model for our player character, and it has this bone structure:
```
"Hips" -> "Spine" -> "Head"
                  -> "Shoulder Right" -> "Arm Right" -> "Hand Right"
                  -> "Shoulder Left" -> "Arm Left" -> "Hand Left"
...
```

If we use the [ThreeJS GLTFLoader](https://threejs.org/docs/#examples/en/loaders/GLTFLoader) to load this model, we'll get back a tree of ThreeJS objects that matches this bone structure.

Now, let's say we want to add a user-changeable hat to our player character's head. We start with setting up
our player-character component:
```clojure

(fn player-character [hat-type]
  [:model {:type :player/character}
    [:model {:type hat-type}]]) ;; This will be at the origin of the character's model!
```

Ruh roh, there's a problem! The `:player/character` model is a ThreeJS object tree itself, but it was created
outside of our Threeagent scene, so we can't just add our `hat-type` model to the entity that represents the
character's head.

To solve this, we can use a portal to navigate the tree structure of any ThreeJS object and then inject our
own entities
```clojure
(fn player-character [hat-type]
  [:model {:type :player/character}
    [:> ["Hips" "Spine" "Head"]
     [:model {:type hat-type}]]]]) 
```

We can also nest portals to clean up our scene graph. Let's say we want to put a weapon in the player's hand:
```clojure
(fn player-character [hat-type]
  [:model {:type :player/character}
    [:> ["Hips" "Spine"]
     [:> ["Head"]
      [:model {:type hat-type}]]
     [:> ["Shoulder Right" "Arm Right" "Hand Right"]
      [:model {:type :player/axe}]]]])
```


With portals, we can cleanly integrate existing ThreeJS objects into our Threeagent scene-graph.

## Recursive portals with `:>>`

Sometimes the full path to the target object is inconvenient to spell out — either because it's deeply nested, the intermediate names are auto-generated (e.g. scene-graph nodes exported from Blender or Sketchfab), or the hierarchy could change between versions of the asset. For these cases Threeagent provides a second portal form, `:>>`, that takes a **single name string** and recursively searches the parent object's descendants for the first match:

```clojure
[:model {:type :player/character}
 [:>> "Hat_Anchor"
  [:model {:type hat-type}]]]
```

This is equivalent to calling `someObject.getObjectByName("Hat_Anchor")` on the parent's ThreeJS tree and using the result as the attachment point. If no descendant with that name exists, a console error is logged and an exception is thrown — same behavior as a broken `:>` path.

### When to use which

| Form | Argument | Traversal | Use when |
|------|----------|-----------|----------|
| `:>`  | Vector of path elements (strings, integers, or `:..`) | Step-by-step, each string uses `getObjectByName` | You know the exact path and want to be explicit, or need integer indices / `:..` to climb |
| `:>>` | Single name string | One recursive `getObjectByName` | You just want to find a uniquely-named descendant, regardless of how deep |

Both forms compose and can be nested freely:

```clojure
[:model {:type :player/character}
 [:>> "Head"
  [:model {:type hat-type}]]
 [:>> "Hand_R"
  [:model {:type :player/axe}]]]
```

## Setting transforms on the portal target

Portals can also accept a **config map** right after the path or name. When present, the map's transform / lifecycle keys are applied to the resolved target itself — no wrapper entity required. This is especially useful for reactively driving a named node's transform from game state:

```clojure
(defn water-gun [nozzle-rot]
  [:model {:type :model/water-gun}
   [:>> "NOZZLE" {:rotation [0 Math/PI nozzle-rot]}]])
```

Each re-render the new config is applied to the resolved `NOZZLE` object. Updating the atom that drives `nozzle-rot` rotates the named node live — no extra `:object` / `:model` wrapping in the scene graph.

### Supported config keys

Only the standard entity-transform / lifecycle keys are reflected onto the target:

- `:position`, `:rotation`, `:scale` — accepts `[x y z]` or a scalar (applied to all three axes).
- `:visible` — boolean.
- `:ref` — called with the resolved target once on mount.
- `:on-added`, `:on-removed`, `:on-updated` — lifecycle hooks, same semantics as on regular entities.

Arbitrary other keys are ignored (the portal doesn't own the target, so we don't reflect material / geometry props onto it).

### Unmount behavior

When a portal with a config is unmounted, its applied transforms **persist** — the target keeps whatever values the portal last set. Portals don't own their target, so we don't snapshot/restore. If you need the original transform restored, capture the values in an `:on-added` callback and reset them in `:on-removed`.

Works with both portal forms:

```clojure
[:instance {:object loaded-glb}
 [:> ["Spine" "Neck"] {:visible false}]      ;; hide via path
 [:>> "Hat_Anchor"    {:scale 1.2}]]         ;; scale via recursive lookup
```

