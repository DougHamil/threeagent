(ns threeagent.impl.virtual-scene
  (:require [reagent.ratom :as ratom]
            [reagent.core :as reagent])
  (:import [goog.structs PriorityQueue]))

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

(deftype Node [context ^Node parent depth id key meta data dirty render reaction children portalPath]
  Object
  (terminal? [_this]
    (= 0 (.-size children)))
  (for-each-child [_this f]
    (doseq [child (es6-iterator-seq (.values children))]
      (f child))))

(deftype Scene [root renderQueue]
  Object
  (enqueueForRender [_ ^Node node ^js render-fn ^js force-replace?]
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

(defn- node-data [comp-key comp-config]
  {:position (:position comp-config [0 0 0])
   :rotation (:rotation comp-config [0 0 0])
   :scale (:scale comp-config [1.0 1.0 1.0])
   :visible (:visible comp-config true)
   :cast-shadow (:cast-shadow comp-config false)
   :receive-shadow (:receive-shadow comp-config false)
   :id (:id comp-config)
   :component-key comp-key
   :component-config (let [c (transient comp-config)]
                       (persistent! (dissoc! c :position :rotation :scale :visible)))}) ;(apply dissoc comp-config non-component-keys)})

(defmulti ->node (fn [^Scene _scene _context ^Node _parent _key form]
                   (let [l (first form)]
                     (cond
                       (= :> l) :portal
                       (keyword? l) :keyword
                       (fn? l) :fn
                       (map? l) :context
                       (sequential? l) :seq
                       (nil? l) :empty-list
                       :else nil))))

(defmethod ->node :default [_scene _context _parent _key form]
  (js/console.error "Invalid object form:" (str form)))

(defmethod ->node :empty-list [_scene _context _parent _key _form])

(defmethod ->node :portal [scene context parent key [_ path & children :as form]]
  (let [depth (if parent
                (inc (.-depth parent))
                0)
        children (filter some? children)
        children-map (js/Map.)
        node (Node. context parent depth nil key (meta form) nil false nil nil children-map path)]
    (when (not (or (string? key)
                   (number? key)))
      (throw (str "^:key must be a string or number, found: " key)))
    (doseq [[idx child] (map-indexed vector children)]
      (when-let [child-node (->node scene context node idx child)]
        (.set children-map (.-key child-node) child-node)))
    node))

(defmethod ->node :context [scene context parent key [subcontext & rest]]
  (->node scene (merge context subcontext) parent key rest))

(defmethod ->node :seq [scene context parent key form]
  (->node scene context parent key (with-meta (into [:object] form) (meta form))))

(defmethod ->node :keyword [scene context parent key form]
  (let [[comp-key & rs] form
        first-child (first rs)
        metadata (meta form)
        key (or (:key metadata) key)
        has-config? (map? first-child)
        comp-config (if has-config? first-child {})
        children (filter some? (if has-config? (rest rs) rs))
        children-map (js/Map.)
        data (node-data comp-key comp-config)
        depth (if parent
                (inc (.-depth parent))
                0)
        node (Node. context parent depth (:id comp-config) key metadata data false nil nil children-map nil)]
    (when (not (or (string? key)
                   (number? key)))
      (throw (str "^:key must be a string or number, found: " key)))
    (doseq [[idx child] (map-indexed vector children)]
      (when-let [child-node (->node scene context node idx child)]
        (.set children-map (.-key child-node) child-node)))
    node))

(defn- fn->render-fn [original-meta f]
  (fn [& args]
    (with-meta
      [:object (apply f args)]
      original-meta)))

(defmethod ->node :fn [scene context parent key form]
  (let [key (or (:key (meta form)) key)
        [f & args] form
        original-meta (meta form)
        outer-reaction-ctx #js {:scene nil, :node nil, :reaction nil, :forceReplace false}
        inner-reaction-ctx #js {:scene nil, :node nil, :reaction nil}
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
        node ^Node (->node scene context parent key (with-meta [:object result] original-meta))]
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

(defmulti ->node-shallow (fn [_key _context form]
                           (let [l (first form)]
                             (cond
                               (fn? l) :fn
                               (= :> l) :portal
                               (keyword? l) :keyword
                               (map? l) :context
                               (sequential? l) :seq
                               (nil? l) :empty-list
                               :else nil))))

(defmethod ->node-shallow :empty-list [_key _context _form])

(defmethod ->node-shallow :context [key context [subcontext & rest]]
  (->node-shallow key (merge context subcontext) rest))

(defmethod ->node-shallow :fn [key context form]
  {:key key
   :context context
   :data (node-data :object {})
   :form form
   :children-keys [[0 form]]})

(defmethod ->node-shallow :seq [key context form]
  (when (seq form)
    (let [m (meta form)]
      (->node-shallow (get-key key m) context (with-meta (into [:object] form) m)))))

(defn- valid-child? [child]
  (and (some? child) (seq child)))

(defmethod ->node-shallow :portal [key context [_ path & children :as form]]
  (let [children (filter valid-child? children)]
   {:key key
    :context context
    :data {}
    :portal-path path
    :form form
    :children-keys (map-indexed #(vector (or (:key (meta %2)) %1) %2)
                                children)}))
  

(defmethod ->node-shallow :keyword [key context form]
  (let [[comp-key & rs] form
        first-child (first rs)
        has-config? (map? first-child)
        comp-config (if has-config? first-child {})
        children (filter valid-child?
                         (if has-config? (rest rs) rs))]
    {:key key
     :context context
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

(defn- add-node! [^Scene scene context ^Node parent-node key form changelog]
  (when-let [node (->node scene context parent-node key form)]
    (.push changelog [node :add nil (.-data node)])
    node))

(defn- remove-node! [^Node node changelog]
  (.push changelog [node :remove (.-data node) nil])
  (set! (.-dirty node) false)
  (dispose-node! node))

(defn- replace-node! [^Scene scene ^Node node new-form changelog]
  (let [parent (.-parent node)
        context (if parent (.-context parent)
                    {})
        key (.-key node)]
    (remove-node! node changelog)
    (let [new-node (add-node! scene context parent key new-form changelog)]
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
    ;; Same form, re-render using the saved render-fn
    (let [render-fn (.-defaultRenderFn node)]
      (update-node! scene node new-form render-fn changelog false))))

(defn- update-node! [^Scene scene ^Node node new-form ^js render-fn changelog force-rerender?]
  (when (or force-rerender?
            (not (same-args? node new-form)))
    (let [key (.-key node)
          children (.-children node)
          parent (.-parent node)
          old-data (.-data node)
          old-context (.-context node)
          old-portal-path (.-portalPath node)
          parent-context (if parent
                           (.-context parent)
                           {})

          rendered-form (if render-fn
                          (apply render-fn (rest new-form))
                          new-form)
          shallow-node (->node-shallow key parent-context rendered-form)
          {new-data :data
           children-keys :children-keys
           new-portal-path :portal-path
           new-context :context} shallow-node
          new-keys-set (reduce (fn [s [k _]] (conj! s k)) (transient #{}) children-keys)
          new-keys (persistent! new-keys-set)]
      (cond
        (not= new-context old-context)
        (replace-node! scene node new-form changelog)

        (not= new-portal-path old-portal-path)
        (replace-node! scene node new-form changelog)

        :else
        (do
          (set! (.-data node) new-data)
          (set! (.-meta node) (meta new-form))
          (set! (.-lastForm node) new-form)
          (.push changelog [node :update old-data new-data])
          ;; Remove children that no longer exist
          (doseq [child-key (es6-iterator-seq (.keys children))]
            (when-not (contains? new-keys child-key)
              (remove-node! (.get children child-key) changelog)
              (.delete children child-key)))
          ;; Update existing children and add new children
          (doseq [[child-key child-form] (:children-keys shallow-node)]
            (if-let [child (.get children child-key)]
              ;; Update existing child
              (update-child-node! scene child child-form changelog)
              ;; Add new child
              (when-let [child-node (add-node! scene old-context node child-key child-form changelog)]
                (.set children child-key child-node)))))))))

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

(defn create
  ([root-fn] (create root-fn {}))
  ([root-fn initial-context]
   (let [scene (Scene. nil (PriorityQueue.))
         root-node (->node scene initial-context nil 0 [root-fn])]
     (set! (.-root scene) root-node)
     scene)))
