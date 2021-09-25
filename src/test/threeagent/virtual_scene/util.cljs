(ns threeagent.virtual-scene.util)

(defn- find-node-by-id* [entity-id node]
  (if (= entity-id (.-id node))
    node
    (->> (es6-iterator-seq (.values (.-children node)))
         (map (partial find-node-by-id* entity-id))
         (filter some?)
         (first))))

(defn find-node-by-id [scene entity-id]
  (find-node-by-id* entity-id (.-root scene)))
