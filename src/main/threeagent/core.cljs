(ns threeagent.core
  (:refer-clojure :exclude [atom])
  (:require [threeagent.scene :as scene]
            [reagent.core :as r]))

(defn atom [d] (r/atom d))
(defn cursor [a p] (r/cursor a p))
(defn reload-scene
  ([scene root-fn] (reload-scene scene root-fn {}))
  ([scene root-fn opts] (scene/reset-scene! scene root-fn opts)))

(defn render
  ([root-fn dom-root] (render root-fn dom-root {}))
  ([root-fn dom-root opts] (scene/render root-fn dom-root opts)))

