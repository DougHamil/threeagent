(ns threeagent.alpha.core
  (:refer-clojure :exclude [atom])
  (:require [threeagent.impl.scene :as scene]
            [reagent.ratom :as ratom]))

(def atom ratom/atom)
(def cursor ratom/cursor)
(def track ratom/track)

(defn render
  ([root-fn dom-root] (render root-fn dom-root {}))
  ([root-fn dom-root opts] (scene/render root-fn dom-root opts)))

