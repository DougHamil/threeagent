(ns threeagent.impl.scene
  (:require [threeagent.impl.virtual-scene :as vscene]
            [threeagent.impl.entities :refer [builtin-entity-types]]
            [threeagent.entity :as entity]
            [threeagent.impl.threejs :as threejs]
            [threeagent.impl.types :refer [Context]]
            [threeagent.impl.system :as systems]
            [clojure.string :as string]
            ["three" :as three]))

(defonce ^:private contexts (array))

(defn- raw-context->context [^Context raw-ctx]
  {:threejs-renderer (.-renderer raw-ctx)
   :threejs-scene (.-sceneRoot raw-ctx)
   :canvas (.-canvas raw-ctx)})

(defn- in-place-update? [^Context ctx ^vscene/Node node]
  (let [entity-type (get (.-entityTypes ctx) (:component-key (.-data node)))]
    (satisfies? entity/IUpdateableEntity entity-type)))

(defn- on-entity-removed [^Context ctx ^vscene/Node node ^three/Object3D old-obj old-component-config]
  ;; Lifecycle Hooks
  (when-let [callback (:on-removed (.-meta node))]
    (callback old-obj))
  (when-let [on-removed (:on-removed old-component-config)]
    (on-removed old-obj))
  (systems/dispatch-on-removed ctx (.-id node) old-obj old-component-config)
  (when (.-isCamera old-obj)
    (when (.-active old-obj)
      (set! (.-camera ctx) (.-lastCamera ctx)))
    (let [cams (.-cameras ctx)]
      (.splice cams (.indexOf cams old-obj) 1))))

(defn- on-entity-added [^Context ctx ^vscene/Node node ^three/Object3D obj component-config]
  ;; Lifecycle Hooks
  (when-let [callback (:on-added (.-meta node))]
    (callback obj))
  (when-let [on-added (:on-added component-config)]
    (on-added obj))
  (systems/dispatch-on-added ctx (.-id node) obj component-config)
  (when (.-isCamera obj)
   (when (.-active obj)
     (set! (.-lastCamera ctx) (.-camera ctx))
     (set! (.-camera ctx) obj))
   (.push (.-cameras ctx) obj)))

(defn- create-entity-object [^Context ctx ^vscene/Node node]
  (let [{:keys [component-config
                component-key
                position
                rotation
                scale]} (.-data node)
        entity-type (get (.-entityTypes ctx) component-key)
        obj (entity/create entity-type component-config)]
    (threejs/set-position! obj position)
    (threejs/set-rotation! obj rotation)
    (threejs/set-scale! obj scale)
    obj))
  
(defn- create-entity
  "Create an entity"
  [^Context ctx ^three/Object3D parent-object ^vscene/Node node]
  (let [{:keys [component-config]} (.-data node)
        obj (create-entity-object ctx node)]
    (.add parent-object obj)
    (set! (.-threejs node) obj)
    (on-entity-added ctx node obj component-config)
    (.for-each-child node (partial create-entity ctx obj))
    obj))

(defn- destroy-entity
  "Destroy an entity"
  [^Context ctx ^vscene/Node node]
  (.for-each-child node (partial destroy-entity ctx))
  (let [{:keys [component-key
                component-config]} (.-data node)
        entity-type (get (.-entityTypes ctx) component-key)
        obj  ^three/Object3D (.-threejs node)]
    (on-entity-removed ctx node obj component-config)
    (.remove (.-parent obj) obj)
    (entity/destroy! entity-type obj)))

(defn- update-entity
  "Update an entity in-place"
  [^Context ctx ^vscene/Node node old-data new-data]
  (let [{:keys [component-config
                component-key
                position
                rotation
                scale]} new-data
        entity-type (get (.-entityTypes ctx) component-key)
        obj  ^three/Object3D (.-threejs node)]
    (on-entity-removed ctx node obj (:component-config old-data))
    (entity/update! entity-type obj component-config)
    (threejs/set-position! obj position)
    (threejs/set-rotation! obj rotation)
    (threejs/set-scale! obj scale)
    (on-entity-added ctx node obj component-config)
    obj))

(defn- transform-entity
  "Update the transformations of the entity in-place"
  [^Context _ctx ^vscene/Node node]
  (let [{:keys [position
                rotation
                scale]} (.-data node)
        obj ^three/Object3D (.-threejs node)]
    (threejs/set-position! obj position)
    (threejs/set-rotation! obj rotation)
    (threejs/set-scale! obj scale)))

(defn- replace-entity
  "Destroy and recreate an entity at a give node in the scene-graph"
  [^Context ctx ^vscene/Node node old-data new-data]
  (let [old-obj (.-threejs node)
        parent-obj (.-parent old-obj)
        children (.-children old-obj)
        new-obj (create-entity-object ctx node)]
    (on-entity-removed ctx node old-obj (:component-config old-data))
    (set! (.-threejs node) new-obj)
    (.remove parent-obj old-obj)
    (.add parent-obj new-obj)
    (when-not (.terminal? node)
      (doseq [child (aclone children)]
        (.add new-obj child)))
    (on-entity-added ctx node new-obj (:component-config new-data))))

(defn- init-scene! [^Context context virtual-scene scene-root]
  (create-entity context scene-root (.-root virtual-scene)))

(defn- update-type [^Context context ^vscene/Node node o n]
  (cond
    (not= (:component-key o)
          (:component-key n)) :replace-entity

    (not= (:component-config o)
          (:component-config n))
    (if (in-place-update? context node)
      :update-entity
      :replace-entity)

    :else :transform-entity))

(defn- apply-change! [^Context context [^vscene/Node node action old-data new-data]]
  (case action
    :add
    (create-entity context ^three/Object3D (.-threejs (.-parent node)) node)

    :remove
    (destroy-entity context node)

    :update 
    (case (update-type context node old-data new-data)
      :replace-entity (try
                        (replace-entity context node old-data new-data)
                        (catch :default ex
                          (js/console.error "Failed to replace entity" ex node)))
      :update-entity (try
                       (update-entity context node old-data new-data)
                       (catch :default ex
                         (js/console.error "Failed to update entity" ex node)))
      :transform-entity (try
                          (transform-entity context node)
                          (catch :default ex
                            (js/console.error "Failed to transform entity" ex node))))))

(defn- animate [^Context context]
  (let [stats (.-stats context)
        clock (.-clock context)
        virtual-scene ^vscene/Scene (.-virtualScene context)
        renderer (.-renderer context)
        composer (.-composer context)
        scene-root (.-sceneRoot context)
        before-render-cb (.-beforeRenderCb context)
        after-render-cb (.-afterRenderCb context)]
    (when stats
      (.begin stats))
    (let [delta-time (.getDelta clock)
          changelog (array)]
      (systems/dispatch-on-tick context delta-time)
      ;; Invoke callbacks
      (when before-render-cb (before-render-cb delta-time))
      ;; Render virtual scene
      (vscene/render! virtual-scene changelog)
      ;; Apply virtual scene changes to ThreeJs scene
      (doseq [change changelog]
        (apply-change! context change))
      ;; Fetch camera after applying the scene changes since it might have been updated
      (let [camera (.-camera context)]
        ;; Render ThreeJS Scene
        (if composer
          (.render composer delta-time)
          (.render renderer scene-root camera)))
      (when after-render-cb (after-render-cb delta-time)))
    (when stats
      (.end stats))))

(defn- get-canvas [dom-root]
  (if (= "canvas" (string/lower-case (.-tagName dom-root)))
    dom-root
    (let [c (.createElement js/document "canvas")]
      (.appendChild dom-root c))))

(defn- set-shadow-map! [renderer shadow-map]
  (when shadow-map
    (let [sm ^js (.-shadowMap renderer)]
      (set! (.-enabled sm) (:enabled shadow-map))
      (set! (.-type sm) (or (:type shadow-map)
                            three/PCFShadowMap)))))


(defn- ^Context create-context [root-fn dom-root {:keys [on-before-render
                                                         on-after-render
                                                         shadow-map
                                                         systems
                                                         entity-types]}]
  (let [canvas (get-canvas dom-root)
        width (.-offsetWidth canvas)
        height (.-offsetHeight canvas)
        renderer (new three/WebGLRenderer (clj->js {:canvas canvas}))
        camera (three/PerspectiveCamera. 75 (/ width height) 0.1 1000)
        cameras (array)
        scene-root (new three/Scene)
        clock (new three/Clock)]
    (.setSize renderer width height)
    (set-shadow-map! renderer shadow-map)
    ;; Systems are initialized before first virtual-render
    (systems/dispatch-init systems {:threejs-renderer renderer
                                    :threejs-scene scene-root
                                    :canvas canvas})
    (let [virtual-scene (vscene/create root-fn)
          context (Context. virtual-scene
                                 scene-root
                                 dom-root nil
                                 canvas camera cameras
                                 clock renderer
                                 on-before-render
                                 on-after-render
                                 (merge builtin-entity-types entity-types)
                                 systems)]
      (init-scene! context virtual-scene scene-root)
      (.push contexts context)
      (.setAnimationLoop renderer #(animate context))
      context)))

(defn- clear-scene! [^Context context ^vscene/Node vscene-root]
  (.for-each-child vscene-root (partial destroy-entity context))
  (.clear (.-sceneRoot context)))

(defn- reset-context! [^Context old-context root-fn {:keys [on-before-render on-after-render shadow-map
                                                            entity-types systems]}]
  (let [scene-root        ^js (.-sceneRoot old-context)
        virtual-scene     ^vscene/Scene (.-virtualScene old-context)
        renderer          ^js (.-renderer old-context)]
    (systems/dispatch-destroy (.-systems old-context)
                              (raw-context->context old-context))
    (clear-scene! old-context (.-root virtual-scene))
    (vscene/destroy! virtual-scene)
    (set-shadow-map! renderer shadow-map)
    (set! (.-cameras old-context) (array))
    (set! (.-systems old-context) systems)
    (set! (.-entityTypes old-context) (merge builtin-entity-types entity-types))
    (systems/dispatch-init systems {:threejs-renderer renderer
                                    :threejs-scene scene-root
                                    :canvas (.-canvas old-context)})
    (let [new-virtual-scene (vscene/create root-fn)]
      (init-scene! old-context new-virtual-scene scene-root)
      (set! (.-virtualScene old-context) new-virtual-scene)
      (set! (.-beforeRenderCb old-context) on-before-render)
      (set! (.-afterRenderCb old-context) on-after-render)
      old-context)))

(defn- find-context [dom-root]
  (first (filter #(= (.-domRoot ^js %) dom-root) contexts)))

(defn- create-or-reset-context [root-fn dom-root config]
  (if-let [existing-context (find-context dom-root)]
    (reset-context! existing-context root-fn config)
    (create-context root-fn dom-root config)))

(defn render [root-fn dom-root config]
  (raw-context->context (create-or-reset-context root-fn dom-root config)))
