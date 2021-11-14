# Contexts

Threeagent provides something called a `context` that can be used to provide arbitrary data to specific
_branches_ of our scene graph. This is somewhat similar to React's [Context](https://reactjs.org/docs/context.html) concept.

Unlike React, in Threeagent there is only one _type_ of context. The context is always a map and can be defined
for any branch of the tree by using a map in the first position of a hiccup vector:
```clojure
[:object
  [{:fruits [:apple :banana]}
   [:object {:id "a"
             :fruit-system {}}]]
  [{:fruits [:banana]}
   [:object {:id "b"
             :fruit-system {}}]]]
```

When the `:fruit-system` `ISystem/on-entity-created` method is called, the `:fruits` key will be `[:apple :banana]` for entity `a`, but it will be `[:banana]` for entity `b`.

This can be particularly useful if we want isolate some state between branches of our scene. For example, if we
our game has multiple rooms, we might want a separate atom to track room-specific state:

```clojure
[:object
  (for [i (range num-rooms)]
    [{:room-state (th/atom {})}
     [enemies]
     [props]])]
```

With this setup, our custom `ISystem` and `IEntityType` can use the provided context map to access
the `:room-state` atom without additional logic to isolate state between rooms.
