# Using Shadows

ThreeJS supports adding shadows to our scenes. We can enable shadows in Threeagent by passing the
`:shadow-map` property when calling `threeagent.core/render`:

```clojure
  (th/render
    root-fn
    (.getElementById js/document "my-canvas")
    {:shadow-map {:enabled true
                  :type three/PCFSoftShadowMap}}))
```

Then, configure a light to cast shadows:
```clojure
(defn lights []
  [:object
    [:ambient-light {:intensity 0.5}]
    [:directional-light {:cast-shadow true}]])
```

Use the `:cast-shadow` and `:receive-shadow` on the built-in geometry components to control shadows:
```clojure
(defn geometry []
  [:object
    [:box {:cast-shadow true
           :receive-shadow true}]
    [:plane {:cast-shadow false
             :receive-shadow true
             :position [0 -1 0}]]])
```
