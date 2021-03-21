(ns threeagent.impl.virtual-scene
  (:require [threeagent.impl.util :refer [log]]
            [medley.core :as medley]
            [reagent.ratom :as ratom]
            [reagent.core :as reagent])
  (:import [goog.structs PriorityQueue]))


(defonce ^:private non-component-keys #{:position :rotation :scale :cast-shadow :receive-shadow})

(defn print-tree
  ([^Node node]
   (print-tree node ""))
  ([^Node node p]
   (let [is-reactive (some? (.-reaction node))]
    (println p "|-"
              (.-key node)
              (str "comp:" (:component-key (.-data node)))
              (str "dirty:" (.-dirty node))
              (str "reactive:" is-reactive))
    (doseq [child (es6-iterator-seq (.values (.-children node)))]
      (print-tree child (str p "\t"))))))

(defn node->path
  ([node]
   (node->path [] node))
  ([path node]
   (if node
     (recur (conj path (.-key node)) (.-parent node))
     (reverse path))))

(defn get-key [key meta] (:key meta key))
      
(deftype Node [^Node parent depth key meta data dirty render reaction children]
  Object
  (terminal? [this]
    (= 0 (.-size children)))
  (for-each-child [this f]
    (doseq [child (es6-iterator-seq (.values children))]
      (f child))))

(deftype Scene [root renderQueue]
  Object
  (enqueueForRender [this ^Node node]
    (set! (.-dirty node) true)
    (.enqueue renderQueue (.-depth node) node)))

(defn get-in-node [^Node node path]
  (if (empty? path)
    node
    (recur (.get (.-children node) (first path)) (rest path))))

(defn get-in-scene [^Scene scene path] (get-in-node (.-root scene) (rest path)))

(defn- eval-fn [original-meta f args]
  (let [result (apply f args)]
    (if (fn? result)
      (recur original-meta result args)
      ^{:__origfn f}
      [(fn re-render [& args]
          (with-meta [:object (apply f args)]
            original-meta))
       result])))

(defn- on-react! [ctx]
  (let [node ^Node (.-node ctx)
        scene ^Scene (.-scene ctx)]
    (.enqueueForRender scene node)))
    
(defn- extract-comp-config [config]
  (let [c (transient config)]
    (persistent! (reduce #(dissoc! %1 %2) c non-component-keys))))

(defn- node-data [comp-key comp-config]
  {:position (:position comp-config [0 0 0])
   :rotation (:rotation comp-config [0 0 0])
   :scale (:scale comp-config [1.0 1.0 1.0])
   :cast-shadow (:cast-shadow comp-config false)
   :receive-shadow (:receive-shadow comp-config false)
   :component-key comp-key
   :component-config (extract-comp-config comp-config)}) ;(apply dissoc comp-config non-component-keys)})

(defmulti ->node (fn [^Scene scene ^Node parent key [l & r]]
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
                (inc (.-depth parent))
                0)
        node (Node. parent depth key metadata data false nil nil children-map)]
    (doseq [[idx child] (medley/indexed children)]
      (when-let [child-node (->node scene node idx child)]
        (.set children-map (.-key child-node) child-node)))
    node))

(defmethod ->node :fn [scene parent key form]
  (let [key (or (:key (meta form)) key)
        [f & args] form
        reaction-ctx ^js (clj->js {:node nil :reaction nil})
        [final-fn result] (ratom/run-in-reaction #(eval-fn (meta form) f args)
                                                 reaction-ctx
                                                 "reaction"
                                                 on-react!
                                                 {:no-cache true})
        node ^Node (->node scene parent key (with-meta [:object result] (meta form)))]
    (set! (.-originalFn node) f)
    (set! (.-render node) final-fn)
    (set! (.-form node) form)
    (set! (.-renderedForm node) result)
    (when-let [reaction (.-reaction reaction-ctx)]
      (set! (.-scene reaction-ctx) scene)
      (set! (.-node reaction-ctx) node)
      (set! (.-reaction node) reaction))
    node))

(defn- form->form-type [[l & r]]
  (cond
    (fn? l) :fn
    (keyword? l) :keyword
    (sequential? l) :seq
    (and (nil? l) (nil? r)) :empty-list
    :else nil))

(defmulti ->node-shallow (fn [key f] (form->form-type f)))

(defmethod ->node-shallow :empty-list [key form])

(defmethod ->node-shallow :fn [key [f & args :as form]]
  {:key key
   :data (node-data :object {})
   :form form
   :children-keys [[0 form]]})

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

(defn- dispose-node! [^Node node]
  (set! (.-disposed node) true)
  (when-let [reaction (.-reaction node)]
    (ratom/dispose! reaction))
  (.for-each-child node dispose-node!))

(defn- add-node! [^Scene scene ^Node parent-node key form changelog]
  (when-let [node (->node scene parent-node key form)]
    (.push changelog [node :add nil (.-data node)])
    node))

(defn- remove-node! [^Node node changelog]
  (.push changelog [node :remove (.-data node) nil])
  (set! (.-data node) nil)
  (set! (.-dirty node) false)
  (dispose-node! node))

(defn- replace-node! [^Scene scene ^Node node new-form changelog]
  (let [parent (.-parent node)
        key (.-key node)]
    (remove-node! node changelog)
    (let [new-node (add-node! scene parent key new-form changelog)]
      (.set (.-children parent) key new-node))))

(defn- diff-fn? [^Node node new-form]
  (let [original-fn (.-originalFn node)]
    (and (some? original-fn)
         (not= original-fn (first new-form)))))

(defn- same-args? [^Node node new-form]
  (= (.-form node)
     new-form))

(defn- update-node! [^Scene scene ^Node node new-form changelog invoked-from-parent?]
  (if (diff-fn? node new-form)
    ;; Completely different render function, replace
    (do
      (set! (.-dirty node) false)
      (replace-node! scene node new-form changelog))
    ;; Same render function, try to update in-place
    (when (or (not invoked-from-parent?)
              (not (same-args? node new-form)))
      (set! (.-dirty node) false)
      (let [render-fn (.-render node)
            new-type (form->form-type new-form)
            rendered-form (if (and render-fn (= :fn new-type))
                            (apply render-fn (rest new-form))
                            new-form)
            old-form (.-renderedForm node)]
        (when (not= rendered-form old-form)
          (let [key (.-key node)
                children (.-children node)
                old-data (.-data node)
                shallow-node (->node-shallow key rendered-form)
                new-data (:data shallow-node)
                current-keys (set (es6-iterator-seq (.keys children)))
                new-keys (set (map first (:children-keys shallow-node)))
                dropped-keys (clojure.set/difference current-keys new-keys)]
            (set! (.-data node) new-data)
            (set! (.-meta node) (meta rendered-form))
            (when (= :fn new-type)
              (let [invocation (if render-fn
                                 (into [(.-originalFn node)] (rest new-form))
                                 new-form)]
                (set! (.-form node) invocation)))
            (set! (.-renderedForm node) new-form)
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
                (update-node! scene child child-form changelog true)
                ;; Add new child
                (when-let [child-node (add-node! scene node child-key child-form changelog)]
                  (.set children child-key child-node))))))))))

(defn- render-node! [^Scene scene ^Node node changelog]
  (let [form (.-form node)]
    (update-node! scene node form changelog false)))

(defn render! [^Scene scene changelog]
  (reagent/flush)
  (let [queue (.-renderQueue scene)]
    (loop [node ^Node (.dequeue queue)]
      (when node
        (do
          (when (.-dirty node)
            (render-node! scene node changelog))
          (recur ^Node (.dequeue queue)))))))

(defn destroy! [^Scene scene]
  (dispose-node! (.-root scene)))

(defn create [root-fn]
  (let [scene (Scene. nil (PriorityQueue.))
        root-node (->node scene nil 0 [root-fn])]
    (set! (.-root scene) root-node)
    scene))
