(ns threeagent.core
  (:refer-clojure :exclude [atom])
  (:require [threeagent.impl.scene :as scene]
            [threeagent.impl.frame-pacer :as frame-pacer]
            [reagent.ratom :as ratom]))

(def atom ratom/atom)
(def cursor ratom/cursor)
(def track ratom/track)

(defn render
  "Renders the threeagent scene at the specified `dom-root`.

   The first argument can be:
   - A single root component function (backwards compatible)
   - A map of `{key -> root-fn}` for multi-scene rendering

   Additional configuration can be provided through the `opts` parameter:
   - `:target-framerate` - Optional. Caps ticks and rendering to the given FPS
     (e.g., 30). When set, frames are skipped until enough time has elapsed.
     Systems receive the correct accumulated delta-time. When nil (default),
     renders at the browser's native refresh rate.
   - `:auto-frame-pacing` - Optional boolean. When true, automatically detects
     the display refresh rate and adapts the framerate to the highest sustainable
     vsync-aligned tier. Drops quickly (5 frames) when over budget and promotes
     slowly (60 frames + 3s cooldown) to prevent oscillation. When combined with
     `:target-framerate`, the target acts as a ceiling.
   - `:render-order` - Optional vector of scene keys specifying render order for
     multi-scene mode (e.g., `[:world :ui]`). Required for deterministic ordering
     with multiple scenes. Defaults to `(keys scenes-map)`.
   - `:render-pipeline` - Optional function for compositing scenes using Three.js
     RenderPipeline (WebGPU). Receives a map of `{scene-key -> pass-node}` where
     each pass-node is a `pass(scene, camera)` TSL node. Must return the output
     node for the pipeline. Works with both single and multi-scene modes.
   - `:scenes` - Optional map of per-scene options keyed by scene key:
     - `:background` - Scene background color (hex number or CSS string)
     - `:clear-depth` - Clear depth buffer before rendering (default true for
       non-primary scenes in sequential mode)
     - `:clear-color` - Clear color buffer before rendering (default false for
       non-primary scenes in sequential mode)

   Returns a context map with:
   - `:threejs-renderer` - The WebGPURenderer instance
   - `:threejs-scene` - The primary scene's Three.js Scene object
   - `:threejs-default-camera` - The primary scene's default camera
   - `:canvas` - The canvas DOM element
   - `:frame-pacer` - Frame pacer instance (if `:auto-frame-pacing` enabled)
   - `:threejs-scenes` - Map of `{key -> Three.js Scene}` for all scenes
   - `:scene-cameras` - Map of `{key -> camera}` for all scenes
   - `:threejs-render-pipeline` - RenderPipeline instance (if `:render-pipeline` provided)

   Examples:
   ```clojure
    ;; Single scene
    (threeagent/render my-root-fn
                       (js/document.getElementById \"app\")
                       {:target-framerate 30})

    ;; Multi-scene with render order
    (threeagent/render {:world world-scene-fn
                        :ui    ui-scene-fn}
                       (js/document.getElementById \"app\")
                       {:render-order [:world :ui]})

    ;; Multi-scene with RenderPipeline compositing
    (threeagent/render {:world world-scene-fn
                        :ui    ui-scene-fn}
                       (js/document.getElementById \"app\")
                       {:render-order [:world :ui]
                        :render-pipeline (fn [{:keys [world ui]}]
                                           (let [world-color (.getTextureNode world \"output\")
                                                 ui-color (.getTextureNode ui \"output\")]
                                             (.add world-color ui-color)))})

    ;; Single scene with post-processing
    (threeagent/render my-root-fn
                       (js/document.getElementById \"app\")
                       {:render-pipeline (fn [{:keys [default]}]
                                           (let [color (.getTextureNode default \"output\")]
                                             (.add color (bloom color))))})
   ```
  "
  ([root-fn dom-root] (render root-fn dom-root {}))
  ([root-fn dom-root opts] (scene/render root-fn dom-root opts)))

(defn frame-pacer-info
  "Returns a snapshot of the frame pacer's current state, or nil if
   auto-frame-pacing is not enabled. The `frame-pacer` argument is the
   `:frame-pacer` value from the context map returned by `render`.

   Returns a map with:
     :phase            - \"calibrating\" or \"active\"
     :display-hz       - Detected display refresh rate (e.g. 120)
     :current-fps      - Current FPS tier target
     :tier-index       - Index into the tiers vector (0 = highest)
     :tiers            - Vector of available FPS tiers, descending
     :over-budget-count  - Consecutive over-budget frame count
     :under-budget-count - Consecutive under-budget frame count
     :actual-interval    - Last measured frame-to-frame interval (ms)"
  [frame-pacer]
  (frame-pacer/info frame-pacer))

