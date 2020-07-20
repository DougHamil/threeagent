# Threeagent
threeagent is a ClojureScript library for building reactive, [three.js](https://threejs.org) applications in a [Reagent](https://reagent-project.github.io/)-like fashion.



# Getting Started
## Installation

Include the threeagent library in your project:

[![Clojars Project](https://clojars.org/doughamil/threeagent/latest-version.svg)](https://clojars.org/doughamil/threeagent)

threeagent depends on three.js, so it will need to be added as a dependency to your project as well.

For [shadow-cljs](http://shadow-cljs.org/), install three.js using npm:
```
npm install --save three
```

For [lein-cljsbuild](https://github.com/emezeske/lein-cljsbuild), add an npm-deps entry for three.js:
```clojure
:cljsbuild {:builds [{...
                      :compiler {...
                                 :install-deps true
                                 :npm-deps {:three "0.100.0"}}}]}
```
 
## Example
```clojure
(ns my-app.core
  (:require [threeagent.alpha.core :as th]))
  
;; Use reactive atom for storing state
(defonce state (th/atom {:ticks 0}))

;; Tick every second
(.setInterval js/window #(swap! state update :ticks inc) 1000)

;; Form-1 component example
(defn color-box [color size]
  [:box {:width size
         :height size
         :depth size
         :material {:color color}])
         
;; Form-2 component example
(defn growing-sphere []
  (let [s (atom 0)]
    (.setInterval js/window #(swap! s inc) 5000)
    (fn []
      [:sphere {:radius @s}])))

;; Root component render function
(defn root []
  [:object {:position [1.0 0 -4.0]
            :rotation [0 (js/Math.sin (:ticks @state)) 0]}
    [color-box "red" 1.0] ; Don't forget to use square brackets!
    [growing-sphere]])
           
           
;; Initialize and begin rendering threeagent scene
(defn init []
  (th/render root (.getElementById js/document "my-canvas")))

(defn ^:dev/after-reload on-code-reload [] ;; For hot-code reloading, just call the render function again
  (th/render root (.getElementById js/document "my-canvas")))
```

[More example projects](https://github.com/DougHamil/threeagent-examples)

# Basic Usage

## Syntax

In threeagent, you use a hiccup-like syntax for defining the components of your scene.
A component is defined by a vector with 3 parts:
1. The first position is the keyword that indicates the type of component. For instance `:box` `:sphere` or `:object`
2. The second position is, optionally, a map that defines the properties of the component. Every component has a `:position` `:rotation` and `:scale`, but other properties can also be defined based on the component type. For instance the `:circle` component expects a `:radius` property to be defined. 
3. The remaining elements in the vector are the child components, allowing you to define your scene as a tree of components.

Here's an example:
```clojure
[:object {:position [0 1.0 0]
          :scale [1.0 2.0 1.0]}
  [:box {:position [2.0 0 0]
         :width 2.0}]
  [:box {:position [2.0 0 0]
         :scale [2.0 1.0 0.5]
         :width 3.0}]]
```

## Initialization

The `threeagent.core/render` function is used to initialize threeagent and start the rendering loop.

For example:
```clojure
(defn root []
  [:object
    [:ambient-light {:intensity 0.8}]
    [:box {:position [0 0 -10]}]])

(defn on-page-load []
  (threeagent/render root (.getElementById js/document "my-canvas")))
  
```

## Functions 

Just like reagent, threeagent allows you to break your scene down into reusable functions.

For example, if we wanted to define a reusable component to represent a row of boxes, we would define a function like this:
```clojure
(defn row-of-boxes [count color]
  [:object
    (for [i (range count)]
     [:box {:position [i 0 0]
            :material {:color color}}])])
```

To use a component function, you simply reference the function from another component:
```clojure
(defn root []
  [:object
    [:object {:position [0 1 0]}
      [row-of-boxes 5 "red"]]
    [:object {:position [0 2 0]}
      [row-of-boxes 8 "blue"]]])
```

Remember to use the square brackets instead of invoking the function directly!

## Custom Components

While threeagent provides a number of built-in components such as `:box` `:object` or `:sphere`, you will eventually want to define your own components.
Using the `defcomponent` macro you can define your own components which you can use to generate new three.js objects.

The `defcomponent` macro is used to build a function that will receive the component property and return an instance of a three.js object.

For example, if we wanted the ability to place custom 3D models in our scene, we could define our own `:model` component:
```clojure
(ns example.core
  (:require-macros [threeagent.alpha.macros :refer [defcomponent]]))

(defcomponent :model [config]
  (let [type (:model-type config)
        mesh (fetch-mesh-of-type type)]
     mesh))
```

We could then use this custom component in our scene:
```clojure
(defn root []
  [:object
    [:model {:model-type "teapot"}]])
```

For more examples, you can check out how [threeagent defines the default components](https://github.com/DougHamil/threeagent/blob/master/src/main/threeagent/impl/component.cljs)



# Built-in Components
threeagent provides a number of components out-of-the-box, allowing you to quickly get started building 3D scenes.

## Common

### `:object`

Properties: `:position` `:rotation` `:scale`

Corresponds to Object3D

### `:group`

Properties: `:position` `:rotation` `:scale`

Corresponds to Group

### `:instance`

Properties: `:object`

Where `:object` is a valid THREE.js object instance (Mesh, Camera, Group, etc).

The value `:object` will be directly injected into the scene graph at this component's location.

## Geometry

### `:box`

Properties: `:width` `:height` `:depth` `:width-segments` `:height-segments` `:depth-segments` `:material`

Corresponds to BoxGeometry

### `:plane`

Properties: `:width` `:height` `:width-segments` `:height-segments` `:material`

Corresponds to PlaneGeometry

### `:sphere`

Properties: `:radius` `:phi-start` `:phi-length` `:theta-start` `:theta-length` `:width-segments` `:height-segments` `:material`

Corresponds to SphereGeometry

### `:cylinder`

Properties: `:radius-top` `:radius-bottom` `:height` `:radial-segments` `:height-segments` `:open-ended?` `:theta-start` `:theta-length` `:material`

Corresponds to CylinderGeometry

### `:circle`

Properties: `:radius` `:segments` `:theta-start` `:theta-length` `:material`

Corresponds to CircleGeometry

### `:cone`

Properties: `:radius` `:height` `:radial-segments` `:height-segments` `:open-ended?` `:theta-start` `:theta-length` `:material`

Corresponds to ConeGeometry

### `:dodecahedron`

Properties: `:radius` `:detail` `:material`

Corresponds to DodecahedronGeometry

### `:icosahedron`

Properties: `:radius` `:detail` `:material`

Corresponds to IcosahedronGeometry

### `:octahedron`

Properties: `:radius` `:detail` `:material`

Corresponds to OctahedronGeometry

### `:tetrahedron`

Properties: `:radius` `:detail` `:material`

Corresponds to TetrahedronGeometry

### `:ring`

Properties: `:inner-radius` `:outer-radius` `:theta-segments` `:phi-segments` `:theta-start` `:theta-length` `:material`

Corresponds to RingGeometry

### `:torus`

Properties: `:radius` `:tube` `:radial-segments` `:tubular-segments` `:arc` `:material`

Corresponds to TorusGeometry

### `:torus-knot`

Properties: `:radius` `:tube` `:radial-segments` `:tubular-segments` `:p` `:q` `:material`

Corresponds to TorusKnotGeometry

### `:shape`

Properties: `:shape` `:material`

Where `:shape` is a valid THREE.js Shape.

Corresponds to ShapeGeometry

## Lights

### `:ambient-light`

Properties: `:color` `:intensity`

Corresponds to AmbientLight

### `:point-light`

Properties: `:color` `:intensity` `:distance` `:decay`

Corresponds to PointLight

### `:hemisphere-light`

Properties: `:sky-color` `:ground-color` `:intensity`

Corresponds to HemisphereLight

### `:rect-area-light`

Properties: `:color` `:intensity` `:width` `:height`

Corresponds to RectAreaLight

### `:spot-light`

Properties: `:color` `:intensity` `:distance` `:angle` `:penumbra` `:decay`

Corresponds to SpotLight

## Cameras

### `:perspective-camera`

Properties: `:fov` `:aspect` `:near` `:far` `:material`

Corresponds to PerspectiveCamera

### `:orthographic-camera`

Properties: `:left` `:right` `:top` `:bottom` `:near` `:far` `:material`

Corresponds to OrthographicCamera


# Tips and Tricks

## Sharing State with Reagent

threeagent's `atom` function actually just returns a reagent reactive atom. This allows you to share your state atoms between reagent and threeagent, meaning you don't need to manually synchronize your reagent-managed user interface and your threeagent-managed 3D scene.
