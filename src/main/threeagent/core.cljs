(ns threeagent.core
  (:refer-clojure :exclude [atom])
  (:require [threeagent.impl.scene :as scene]
            [threeagent.impl.types :refer [Context]]
            [reagent.ratom :as ratom]))

(def atom ratom/atom)
(def cursor ratom/cursor)
(def track ratom/track)

(defn render
  "Renders the threeagent scene at the specified `dom-root` using
   the `root-fn` as the root component function.

   Additional configuration can be provided through the `opts` parameter

   Example:
   ```clojure
    (threeagent/render my-root-fn (js/document.getElementById \"app\"))
   ```
  "
  ([root-fn dom-root] (render root-fn dom-root {}))
  ([root-fn dom-root opts] (scene/render root-fn dom-root opts)))

