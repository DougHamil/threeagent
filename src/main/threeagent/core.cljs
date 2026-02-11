(ns threeagent.core
  (:refer-clojure :exclude [atom])
  (:require [threeagent.impl.scene :as scene]
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

   Example:
   ```clojure
    (threeagent/render my-root-fn
                       (js/document.getElementById \"app\")
                       {:target-framerate 30})
   ```
  "
  ([root-fn dom-root] (render root-fn dom-root {}))
  ([root-fn dom-root opts] (scene/render root-fn dom-root opts)))

