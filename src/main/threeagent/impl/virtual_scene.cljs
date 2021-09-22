(ns threeagent.impl.virtual-scene
  (:require [medley.core :as medley]
            [clojure.set :as set]
            [reagent.ratom :as ratom]
            [reagent.core :as reagent])
  (:import [goog.structs PriorityQueue]))

(defonce ^:private non-component-keys #{:position :rotation :scale})

(defn print-tree
  ([^Node node]
   (print-tree node ""))
  ([^Node node p]
   (let [is-reactive (and (some? (.-reactions node))
                          (seq (.-reactions node)))]
     (println p "|-"
              (.-key node)
              (str "comp:" (:component-key (.-data node)))
              (str "id:" (:id (.-data node)))
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

(deftype RenderQueueEntry [^Node node ^js renderFn ^js forceReplace]
  Object)

(deftype Node [^Node parent depth id key meta data dirty render reaction children]
  Object
  (terminal? [_this]
    (= 0 (.-size children)))
  (for-each-child [_this f]
    (doseq [child (es6-iterator-seq (.values children))]
      (f child))))

(deftype Scene [root renderQueue]
  Object
  (enqueueForRender [this ^Node node ^js render-fn ^js force-replace?]
    (set! (.-dirty node) true)
    (.enqueue renderQueue (.-depth node) (RenderQueueEntry. node render-fn force-replace?))))

(defn get-in-node [^Node node path]
  (if (empty? path)
    node
    (recur (.get (.-children node) (first path)) (rest path))))

(defn get-in-scene [^Scene scene path] (get-in-node (.-root scene) (rest path)))

(defn- on-react! [render-fn ctx]
  (let [node ^Node (.-node ctx)
        scene ^Scene (.-scene ctx)]
    (.enqueueForRender scene node render-fn ^js (.-forceReplace ctx))))

(defn- extract-comp-config [config]
  (let [c (transient config)]
    (persistent! (reduce #(dissoc! %1 %2) c non-component-keys))))

(defn- node-data [comp-key comp-config]
  {:position (:position comp-config [0 0 0])
   :rotation (:rotation comp-config [0 0 0])
   :scale (:scale comp-config [1.0 1.0 1.0])
   :cast-shadow (:cast-shadow comp-config false)
   :receive-shadow (:receive-shadow comp-config false)
   :id (:id comp-config)
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
        node (Node. parent depth (:id comp-config) key metadata data false nil nil children-map)]
    (if (not (or (string? key)
                 (number? key)))
      (throw (str "^:key must be a string or number, found: " key)))
    (doseq [[idx child] (medley/indexed children)]
      (when-let [child-node (->node scene node idx child)]
        (.set children-map (.-key child-node) child-node)))
    node))

(defn- fn->render-fn [original-meta f]
  (fn [& args]
    (with-meta
      [:object (apply f args)]
      original-meta)))

(defmethod ->node :fn [scene parent key form]
  (let [key (or (:key (meta form)) key)
        [f & args] form
        original-meta (meta form)
        outer-reaction-ctx ^js (clj->js {:scene nil :node nil :reaction nil :forceReplace false})
        inner-reaction-ctx ^js (clj->js {:scene nil :node nil :reaction nil})
        outer-render-fn (fn->render-fn original-meta f)
        outer-result (ratom/run-in-reaction #(apply f args)
                                            outer-reaction-ctx
                                            "reaction"
                                            (partial on-react! outer-render-fn)
                                            {:no-cache true})
        inner-render-fn (when (fn? outer-result)
                          (fn->render-fn original-meta outer-result))
        inner-result (when (fn? outer-result)
                       (ratom/run-in-reaction #(apply outer-result args)
                                              inner-reaction-ctx
                                              "reaction"
                                              (partial on-react! inner-render-fn)
                                              {:no-cache true}))
        default-render-fn (or inner-render-fn outer-render-fn)
        result (or inner-result outer-result)
        node ^Node (->node scene parent key (with-meta [:object result] original-meta))]
    (when inner-render-fn
      (set! (.-forceReplace outer-reaction-ctx) true))
    (set! (.-originalFn node) f)
    (set! (.-defaultRenderFn node) default-render-fn)
    (set! (.-form node) form)
    (set! (.-lastForm node) form)
    (set! (.-reactions node) (array))
    (when-let [reaction (.-reaction outer-reaction-ctx)]
      (set! (.-scene outer-reaction-ctx) scene)
      (set! (.-node outer-reaction-ctx) node)
      (.push (.-reactions node) reaction))
    (when-let [reaction (.-reaction inner-reaction-ctx)]
      (set! (.-scene inner-reaction-ctx) scene)
      (set! (.-node inner-reaction-ctx) node)
      (.push (.-reactions node) reaction))
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
        children (filter #(and (some? %) (seq %))
                         (if (map? first-child) (rest rs) rs))]
    {:key key
     :data (node-data comp-key comp-config)
     :form form
     :children-keys (map-indexed #(vector (or (:key (meta %2)) %1) %2)
                                 children)}))

(defn- dispose-node! [^Node node]
  (set! (.-disposed node) true)
  (when-let [reactions (.-reactions node)]
    (doseq [r reactions]
      (ratom/dispose! r)))
  (.for-each-child node dispose-node!))

(defn- add-node! [^Scene scene ^Node parent-node key form changelog]
  (when-let [node (->node scene parent-node key form)]
    (.push changelog [node :add nil (.-data node)])
    node))

(defn- remove-node! [^Node node changelog]
  (.push changelog [node :remove (.-data node) nil])
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
  (= (.-lastForm node)
     new-form))

(declare update-node!)

(defn- update-child-node! [^Scene scene ^Node node new-form changelog]
  (if (diff-fn? node new-form)
    ;; Completely different render function, replace
    (replace-node! scene node new-form changelog)
    ;; Same form, re-render using the default render-fn
    (let [render-fn (.-defaultRenderFn node)]
      (update-node! scene node new-form render-fn changelog false))))

(defn- update-node! [^Scene scene ^Node node new-form ^js render-fn changelog force-rerender?]
  (when (or force-rerender?
            (not (same-args? node new-form)))
    (let [key (.-key node)
          children (.-children node)
          old-data (.-data node)
          current-keys (set (es6-iterator-seq (.keys children)))
          rendered-form (if render-fn
                          (apply render-fn (rest new-form))
                          new-form)
          shallow-node (->node-shallow key rendered-form)
          new-data (:data shallow-node)
          new-keys (set (map first (:children-keys shallow-node)))
          dropped-keys (set/difference current-keys new-keys)]
      (set! (.-data node) new-data)
      (set! (.-meta node) (meta new-form))
      (set! (.-lastForm node) new-form)
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
          (update-child-node! scene child child-form changelog)
          ;; Add new child
          (when-let [child-node (add-node! scene node child-key child-form changelog)]
            (.set children child-key child-node)))))))

(defn- render-node! [^Scene scene ^Node node ^js render-fn force-replace? changelog]
  (let [new-form (.-form node)]
    (if (or force-replace?
            (diff-fn? node new-form))
      (do
        (set! (.-dirty node) false)
        ;; Completely different render function, replace
        (replace-node! scene node new-form changelog))
      ;; Same function, re-render using the supplied render-fn
      (do
        (set! (.-dirty node) false)
        (update-node! scene node new-form render-fn changelog true)))))

(defn render! [^Scene scene changelog]
  (reagent/flush)
  (let [queue (.-renderQueue scene)]
    (loop [entry ^RenderQueueEntry (.dequeue queue)]
      (when entry
        (when-let [node ^Node (.-node entry)]
          (when-not (.-disposed node)
            (render-node! scene node (.-renderFn entry) (.-forceReplace entry) changelog))
          (recur ^RenderQueueEntry (.dequeue queue)))))))

(defn destroy! [^Scene scene]
  (dispose-node! (.-root scene)))

(defn create [root-fn]
  (let [scene (Scene. nil (PriorityQueue.))
        root-node (->node scene nil 0 [root-fn])]
    (set! (.-root scene) root-node)
    scene))
