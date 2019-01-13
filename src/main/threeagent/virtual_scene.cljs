(ns threeagent.virtual-scene
  (:require [threeagent.util :refer [$ $! log]]
            [medley.core :as medley]
            [reagent.ratom :as ratom])
  (:import [goog.structs PriorityQueue]))

(defonce ^:private non-component-keys #{:position :rotation :scale})

(defn print-tree
  ([node]
   (print-tree node ""))
  ([node p]
   (let [is-reactive (some? ($ node "reaction"))]
    (println p "|-"
              ($ node "key")
              (str "dirty:" ($ node "dirty"))
              (str "reactive:" is-reactive)
              ($ node "render"))
    (doseq [child (es6-iterator-seq (.values ($ node "children")))]
      (print-tree child (str p "\t"))))))

(defn node->path
  ([node]
   (node->path [] node))
  ([path node]
   (if node
     (recur (conj path ($ node "key")) ($ node "parent"))
     (reverse path))))

(defn get-in-node [node path]
  (if (empty? path)
    node
    (recur (.get ($ node "children") (first path)) (rest path))))

(defn get-key [key meta] (or (:key meta)) key)
      
(deftype Node [parent depth key meta data dirty render reaction children]
  Object
  (for-each-child [this f]
    (doseq [child (es6-iterator-seq (.values children))]
      (f child))))

(deftype Scene [root render-queue]
  Object
  (enqueue-for-render [this node]
    ($! node "dirty" true)
    #_(println (node->path node))
    (.enqueue render-queue ($ node "depth") node)))

(defn- eval-fn [f args]
  (let [result (apply f args)]
    (if (fn? result)
      (recur result args)
      [f result])))

(defn- on-react! [ctx]
  (let [node ($ ctx "node")
        scene ($ ctx "scene")]
    (.enqueue-for-render scene node)))
    
(defn- extract-comp-config [config]
  (let [c (transient config)]
    (persistent! (reduce #(dissoc! %1 %2) c non-component-keys))))

(defn- node-data [comp-key comp-config]
  {:position (:position comp-config [0 0 0])
   :rotation (:rotation comp-config [0 0 0])
   :scale (:scale comp-config [1.0 1.0 1.0])
   :component-key comp-key
   :component-config (extract-comp-config comp-config)}) ;(apply dissoc comp-config non-component-keys)})

(defmulti ->node (fn [scene parent key [l & r]]
                   (cond
                      (keyword? l) :keyword
                      (fn? l) :fn
                      (sequential? l) :seq
                      (and (nil? l) (nil? r)) :empty-list
                      :else nil)))

(defmethod ->node :default [scene parent key form]
  (println "Invalid object form:" form))

(defmethod ->node :empty-list [scene parent key form])

(defmethod ->node :seq [scene parent key form]
  (->node scene parent key (with-meta (into [:object] form) (meta form))))

(defmethod ->node :keyword [scene parent key form]
  (let [[comp-key & rs] form
        first-child (first rs)
        metadata (meta form)
        key (or (:key metadata) key)
        comp-config (if (map? first-child) first-child {})
        children (filter some? (if (map? first-child) (rest rs) rs))
        children-map (js/Map.)
        data (node-data comp-key comp-config)
        depth (if parent
                (inc ($ parent "depth"))
                0)
        node (Node. parent depth key metadata data false nil nil children-map)]
    (doseq [[idx child] (medley/indexed children)]
      (when-let [child-node (->node scene node idx child)]
        (.set children-map ($ child-node "key") child-node)))
    node))

(defmethod ->node :fn [scene parent key form]
  (let [key (or (:key (meta form)) key)
        [f & args] form
        reaction-ctx (clj->js {:node nil :reaction nil})
        [final-fn result] (ratom/run-in-reaction #(eval-fn f args) reaction-ctx "reaction" on-react! {:no-cache true})
        node (->node scene parent key result)]
    ($! node "render" final-fn)
    ($! node "form" form)
    ($! node "rendered-form" result)
    (when-let [reaction ($ reaction-ctx "reaction")]
      ($! reaction-ctx "scene" scene)
      ($! reaction-ctx "node" node)
      ($! node "reaction" reaction))
    node))

(defmulti ->node-shallow (fn [key [l & r]]
                          (cond
                            (fn? l) :fn
                            (keyword? l) :keyword
                            (sequential? l) :seq
                            (and (nil? l) (nil? r)) :empty-list
                            :else nil)))

(defmethod ->node-shallow :empty-list [key form])

(defmethod ->node-shallow :fn [key form])

(defmethod ->node-shallow :seq [key form]
  (when-not (empty? form)
    (let [m (meta form)]
      (->node-shallow (get-key key m) (with-meta (into [:object] form) m)))))

(defmethod ->node-shallow :keyword [key form]
  (let [[comp-key & rs] form
        first-child (first rs)
        comp-config (if (map? first-child) first-child {})
        children (filter #(and (some? %) (not (empty? %)))
                         (if (map? first-child) (rest rs) rs))]
    {:key key
     :data (node-data comp-key comp-config)
     :form form
     :children-keys (map-indexed #(vector (or (:key (meta %2)) %1) %2)
                                children)}))

(defn- dispose-node! [node]
  ($! node "disposed" true)
  (when-let [reaction ($ node "reaction")]
    (ratom/dispose! reaction))
  (.for-each-child node dispose-node!))

(defn- add-node! [scene parent-node key form changelog]
  (when-let [node (->node scene parent-node key form)]
    (.push changelog [node :add nil ($ node "data")])
    node))

(defn- remove-node! [node changelog]
  (.push changelog [node :remove ($ node "data") nil])
  ($! node "data" nil)
  ($! node "dirty" false)
  (dispose-node! node))

(defn- update-node! [scene node new-form changelog]
  ($! node "dirty" false)
  (let [render-fn ($ node "render")
        rendered-form (if render-fn
                        (apply render-fn (rest new-form))
                        new-form)
        old-form ($ node "rendered-form")]
    (when (not= rendered-form old-form)
      (let [key ($ node "key")
            children ($ node "children")
            old-data ($ node "data")
            shallow-node (->node-shallow key rendered-form)
            new-data (:data shallow-node)
            current-keys (set (es6-iterator-seq (.keys children)))
            new-keys (set (map first (:children-keys shallow-node)))
            dropped-keys (clojure.set/difference current-keys new-keys)]
        ($! node "data" new-data)
        (when render-fn
          ($! node "form" (into [render-fn] (rest new-form))))
        ($! node "rendered-form" new-form)
        (.push changelog [node :update old-data new-data])
        ;; Remove children that no longer exist
        (doseq [child-key dropped-keys]
          (let [child-node (.get children child-key)]
            (remove-node! child-node changelog))
          (.delete children child-key))
        ;; Update existing children and add new children
        (doseq [[child-key child-form] (:children-keys shallow-node)]
          (if-let [child (.get children child-key)]
            ;; Update existing child
            (update-node! scene child child-form changelog)
            ;; Add new child
            (when-let [child-node (add-node! scene node child-key child-form changelog)]
              (.set children child-key child-node))))))))

(defn- render-node! [scene node changelog]
  (let [form ($ node "form")]
    (update-node! scene node form changelog)))

(defn render! [scene changelog]
  (ratom/flush!)
  (let [queue ($ scene "render_queue")]
    (loop [node (.dequeue queue)]
      (when node
        (do
          (when ($ node "dirty")
            (render-node! scene node changelog))
          (recur (.dequeue queue)))))))

(defn destroy! [scene]
  (dispose-node! ($ scene "root")))

(defn create [root-fn]
  (let [scene (Scene. nil (PriorityQueue.))
        root-node (->node scene nil 0 [root-fn])]
    ($! scene "root" root-node)
    scene))

