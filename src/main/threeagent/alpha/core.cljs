(ns threeagent.alpha.core
  (:refer-clojure :exclude [atom])
  (:require [threeagent.impl.scene :as scene]
            [reagent.ratom :as ratom]))

(defn atom [d] (ratom/atom d))
(defn cursor [a p] (ratom/cursor a p))
(defn reload-scene
  ([scene root-fn] (reload-scene scene root-fn {}))
  ([scene root-fn opts] (scene/reset-scene! scene root-fn opts)))

(defn render
  ([root-fn dom-root] (render root-fn dom-root {}))
  ([root-fn dom-root opts] (scene/render root-fn dom-root opts)))

