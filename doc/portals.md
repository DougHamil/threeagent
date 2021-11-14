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

