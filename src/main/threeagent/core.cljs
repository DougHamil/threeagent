(ns threeagent.core
  (:refer-clojure :exclude [atom])
  (:require [threeagent.impl.scene :as scene]
            [threeagent.impl.frame-pacer :as frame-pacer]
            [reagent.ratom :as ratom]))

(def atom ratom/atom)
(def cursor ratom/cursor)
(def track ratom/track)

(defn render
  "Renders the threeagent scene at the specified `dom-root` using
   the `root-fn` as the root component function.

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

   Example:
   ```clojure
    (threeagent/render my-root-fn
                       (js/document.getElementById \"app\")
                       {:target-framerate 30})

    ;; Automatic frame pacing
    (threeagent/render my-root-fn
                       (js/document.getElementById \"app\")
                       {:auto-frame-pacing true})
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

