# Multi-Scene Rendering

Threeagent supports rendering multiple independent scenes that share a single `WebGPURenderer` and are composited together. This is useful when we need separate scene graphs for different layers of our application, such as a 3D world and a UI overlay.

Each scene gets its own independent:
- Virtual scene tree and Three.js `Scene` object
- Camera tracking (active camera, default camera)
- Entity registry (avoids ID collisions between scenes)

Shared across all scenes:
- `WebGPURenderer`, canvas, clock, animation loop
- Entity types, systems
- Frame pacing

## Basic Setup

To render multiple scenes, pass a map of `{key -> root-fn}` as the first argument to `th/render` instead of a single function:

```clojure
(defn world-scene []
  [:object
    [:ambient-light {:intensity 0.8}]
    [:box {:position [0 0 -5]}]])

(defn ui-scene []
  [:object
    [:sprite {:position [0 2 -3]}]])

(def ctx
  (th/render {:world world-scene
              :ui    ui-scene}
             (.getElementById js/document "app")
             {:render-order [:world :ui]}))
```

The `:render-order` option specifies the order in which scenes are rendered. The first scene in the order is the _primary_ scene, which determines the values of `:threejs-scene` and `:threejs-default-camera` in the returned context map for backwards compatibility.

## Returned Context

When using multi-scene rendering, the context map returned by `th/render` includes additional keys:

```clojure
{:threejs-renderer renderer           ;; shared WebGPURenderer
 :threejs-scene world-scene           ;; primary scene's Three.js Scene
 :threejs-default-camera world-camera ;; primary scene's default camera
 :canvas canvas
 :frame-pacer pacer

 ;; Multi-scene keys:
 :threejs-scenes {:world world-scene
                  :ui ui-scene}       ;; all Three.js Scene objects
 :scene-cameras {:world world-camera
                 :ui ui-camera}       ;; all active cameras
 :threejs-render-pipeline pipeline}   ;; RenderPipeline (if :render-pipeline provided)
```

## Sequential Rendering

By default, when no `:render-pipeline` option is provided, scenes are rendered sequentially. The first scene renders normally, and subsequent scenes render on top with the depth buffer cleared between them. This is suitable for simple overlay scenarios:

```clojure
(th/render {:world world-scene
            :ui    ui-scene}
           canvas
           {:render-order [:world :ui]})
```

The world scene renders first, then the depth buffer is cleared and the UI scene renders on top.

## Per-Scene Options

We can configure per-scene rendering behavior through the `:scenes` option:

```clojure
(th/render {:world world-scene
            :ui    ui-scene}
           canvas
           {:render-order [:world :ui]
            :scenes {:ui {:background 0x000000   ;; Scene background color
                          :clear-depth true       ;; Clear depth before rendering (default: true)
                          :clear-color false}}})  ;; Clear color before rendering (default: false)
```

The `:background` value is applied to the Three.js `Scene.background` property and accepts a hex color number or CSS color string. It is only set when explicitly provided; by default, scene backgrounds are transparent (`null`).

## RenderPipeline Compositing

For more control over how scenes are composited, we can use the `:render-pipeline` option. This uses Three.js's `RenderPipeline` with TSL (Three Shading Language) nodes, giving us access to effects like bloom, tone mapping, and custom blend modes.

The `:render-pipeline` option takes a function that receives a map of `{scene-key -> pass-node}`, where each pass node is a `pass(scene, camera)` TSL node. The function must return the output node for the pipeline:

```clojure
(th/render {:world world-scene
            :ui    ui-scene}
           canvas
           {:render-order [:world :ui]
            :render-pipeline
            (fn [{:keys [world ui]}]
              (let [world-color (.getTextureNode world "output")
                    ui-color (.getTextureNode ui "output")]
                (.add world-color ui-color)))})
```

RenderPipeline requires WebGPU support. The pipeline is created lazily on the first render frame and is automatically rebuilt when a scene's active camera changes.

### Single-Scene Post-Processing

The `:render-pipeline` option also works with single-scene rendering. In this case, the scene key is `:default`:

```clojure
(ns my-app
  (:require [threeagent.core :as th]
            ["three/addons/tsl/display/BloomNode.js" :refer [bloom]]))

(th/render my-scene-fn
           canvas
           {:render-pipeline
            (fn [{:keys [default]}]
              (let [color (.getTextureNode default "output")]
                (.add color (bloom color))))})
```

## Scene Key Context

In multi-scene mode, each scene's entities automatically receive a `:threeagent/scene-key` in their [context](./contexts.md). This is useful in custom `ISystem` and `IEntityType` implementations to determine which scene an entity belongs to:

```clojure
(deftype MySystem [state]
  ISystem
  ;; ...
  (on-entity-added [_ ctx entity-id obj config]
    (let [scene-key (:threeagent/scene-key ctx)]
      ;; scene-key will be :world, :ui, etc.
      (swap! state assoc-in [scene-key entity-id] obj))))
```

In single-scene mode, the context does not include `:threeagent/scene-key`, preserving backwards compatibility.

## Reactivity

Each scene's virtual tree is independent. A Reagent atom dereferenced in one scene's component functions will only trigger re-renders in that scene when it changes. This means state changes are efficiently scoped to the scene that depends on them.

## Hot Reload

Threeagent's hot-reload support (`th/render` called again on the same DOM root) handles transitions between single and multi-scene modes. All existing scenes are torn down and new ones are created, so we can freely switch between configurations during development.

## Systems

Systems receive the full set of scenes during initialization. The context map passed to `ISystem/init` and `ISystem/destroy` includes a `:threejs-scenes` key with a map of all Three.js Scene objects:

```clojure
(init [this context]
  (let [all-scenes (:threejs-scenes context)  ;; {:world scene, :ui scene}
        renderer   (:threejs-renderer context)]
    ;; Setup that spans multiple scenes...
    ))
```

The `:threejs-scene` key continues to point to the primary scene for backwards compatibility.

## Complete Example

Here's a full namespace showing a game with a 3D world scene and a HUD overlay scene. Each scene has
its own component hierarchy and reacts to shared state independently:

```clojure
(ns my-game.core
  (:require [threeagent.core :as th]))

;; Shared application state
(defonce state (th/atom {:score 0
                         :player-pos [0 0 -5]
                         :health 100}))

;; ---------------------------------------------------------------------------
;; World scene — 3D gameplay
;; ---------------------------------------------------------------------------

(defn enemy [id position]
  [:box {:id id
         :position position
         :material {:color "red"}
         :dims [0.5 0.5 0.5]}])

(defn player []
  (let [pos @(th/cursor state [:player-pos])]
    [:object {:position pos}
      [:sphere {:radius 0.3
                :material {:color "blue"}}]]))

(defn world-scene []
  [:object
    [:ambient-light {:intensity 0.5}]
    [:directional-light {:position [5 10 5]
                         :intensity 0.8}]
    [:perspective-camera {:position [0 5 5]
                          :active true}]
    [player]
    [enemy "enemy-1" [3 0 -5]]
    [enemy "enemy-2" [-2 0 -8]]])

;; ---------------------------------------------------------------------------
;; HUD scene — 2D UI overlay
;; ---------------------------------------------------------------------------

(defn score-display []
  (let [score @(th/cursor state [:score])]
    [:text {:value (str "Score: " score)
            :position [-3 2 -5]
            :material {:color "white"}}]))

(defn health-bar []
  (let [health @(th/cursor state [:health])]
    [:object {:position [-3 -2 -5]}
      [:box {:dims [(* 2.0 (/ health 100)) 0.2 0.01]
             :material {:color (if (> health 30) "green" "red")}}]]))

(defn hud-scene []
  [:object
    [:orthographic-camera {:active true}]
    [score-display]
    [health-bar]])

;; ---------------------------------------------------------------------------
;; Initialization
;; ---------------------------------------------------------------------------

(defn ^:dev/after-load reload []
  (th/render {:world world-scene
              :hud   hud-scene}
             (.getElementById js/document "app")
             {:render-order [:world :hud]}))

(defn init []
  ;; Example: increment score every second
  (.setInterval js/window #(swap! state update :score inc) 1000)
  (reload))
```

Changing `:score` in the state atom will only re-render the `score-display` component in the HUD scene. The world scene is unaffected. Likewise, moving the player only triggers updates in the world scene.

## Single-Scene Compatibility

Existing single-scene `th/render` calls continue to work identically:

```clojure
;; This still works exactly as before
(th/render my-root-fn canvas {:entity-types my-types})
```

Internally, a single root function is wrapped as `{:default root-fn}` with a render order of `[:default]`. The rendering path for single-scene mode is unchanged.
