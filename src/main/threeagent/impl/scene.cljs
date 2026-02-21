(ns threeagent.impl.scene
  (:require [threeagent.impl.virtual-scene :as vscene]
            [threeagent.impl.entities :refer [builtin-entity-types]]
            [threeagent.entity :as entity]
            [threeagent.impl.threejs :as threejs]
            [threeagent.impl.types :refer [Context SceneContext]]
            [threeagent.impl.system :as systems]
            [threeagent.impl.registry :as registries]
            [threeagent.impl.frame-pacer :as frame-pacer]
            [clojure.string :as string]
            ["three/webgpu" :as three]
            ["three/tsl" :refer [pass]]))

(defonce ^:private contexts (array))

;; ---------------------------------------------------------------------------
;; Per-scene field accessor (reads from activeSceneCtx on Context)
;; ---------------------------------------------------------------------------

(defn- active-scene-ctx ^SceneContext [^Context ctx]
  (.-activeSceneCtx ctx))

;; ---------------------------------------------------------------------------
;; Context map construction
;; ---------------------------------------------------------------------------

(defn- raw-context->context [^Context raw-ctx]
  (let [scenes (.-scenes raw-ctx)
        primary-sctx ^SceneContext (get scenes (.-primarySceneKey raw-ctx))]
    (cond-> {:threejs-renderer (.-renderer raw-ctx)
             :threejs-scene (.-sceneRoot primary-sctx)
             :threejs-default-camera (.-defaultCamera primary-sctx)
             :canvas (.-canvas raw-ctx)
             :frame-pacer (.-framePacer raw-ctx)
             :threejs-scenes (reduce-kv (fn [m k ^SceneContext sctx] (assoc m k (.-sceneRoot sctx))) {} scenes)
             :scene-cameras (reduce-kv (fn [m k ^SceneContext sctx] (assoc m k (.-camera sctx))) {} scenes)}
      (.-renderPipeline raw-ctx)
      (assoc :threejs-render-pipeline (.-renderPipeline raw-ctx)))))

;; ---------------------------------------------------------------------------
;; Entity helpers (unchanged logic, but per-scene fields via activeSceneCtx)
;; ---------------------------------------------------------------------------

(defn- in-place-update? [^Context ctx ^vscene/Node node]
  (let [entity-type (get (.-entityTypes ctx) (:component-key (.-data node)))]
    (satisfies? entity/IUpdateableEntityType entity-type)))

(defn- portal? [^vscene/Node node]
  (some? (.-portalPath node)))

(defn- on-entity-removed [^Context ctx ^vscene/Node node ^three/Object3D old-obj old-component-config]
  ;; Lifecycle Hooks
  (when-let [callback (:on-removed (.-meta node))]
    (callback old-obj))
  (when-let [on-removed (:on-removed old-component-config)]
    (on-removed old-obj))
  (let [callbacks (systems/dispatch-on-removed ctx (.-context node) (.-id node) old-obj old-component-config)]
    (when (.-isCamera old-obj)
      (let [sctx ^js (active-scene-ctx ctx)]
        (when (.-active old-obj)
          (set! (.-camera sctx) (.-lastCamera sctx)))
        (let [cams (.-cameras sctx)]
          (.splice cams (.indexOf cams old-obj) 1))))
    callbacks))

(defn- on-entity-added [^Context ctx ^vscene/Node node ^three/Object3D obj component-config]
  ;; Lifecycle Hooks
  (when-let [callback (:on-added (.-meta node))]
    (callback obj))
  (when-let [on-added (:on-added component-config)]
    (on-added obj))
  (when-let [ref (:ref component-config)]
    (ref obj))
  (let [callbacks (systems/dispatch-on-added ctx (.-context node) (.-id node) obj component-config)]
    (when (.-isCamera obj)
      (let [sctx ^js (active-scene-ctx ctx)]
        (when (.-active obj)
          (set! (.-lastCamera sctx) (.-camera sctx))
          (set! (.-camera sctx) obj))
        (.push (.-cameras sctx) obj)))
    callbacks))

(defn- on-entity-updated [^Context ctx ^vscene/Node node ^three/Object3D obj component-config]
  ;; Lifecycle Hooks
  (when-let [callback (:on-updated (.-meta node))]
    (callback obj))
  (when-let [on-added (:on-updated component-config)]
    (on-added obj))
  (systems/dispatch-on-updated ctx (.-context node) (.-id node) obj component-config))

(defn- create-entity-object [^Context ctx ^vscene/Node node]
  (let [{:keys [component-config
                component-key
                position
                rotation
                scale
                visible]} (.-data node)]
    (if-let [entity-type (get (.-entityTypes ctx) component-key)]
      (let [obj (entity/create entity-type (.-context node) component-config)]
        (threejs/set-position! obj position)
        (threejs/set-rotation! obj rotation)
        (threejs/set-scale! obj scale)
        (set! (.-visible obj) visible)
        obj)
      (throw (js/Error. (str "Cannot find entity-type for keyword '" (str component-key) "'")
                        node)))))

(defn- resolve-portal-object [^three/Object3D default-parent ^vscene/Node node]
  (let [path (.-portalPath node)
        parent (threejs/get-in default-parent path)]
    (when-not parent
      (js/console.error (str "Invalid portal path '" path "'")
                        default-parent)
      (throw (js/Error. (str "Portal path '" path "' is invalid."))))
    parent))

(defn- create-entity
  ([^Context ctx ^three/Object3D parent-object ^vscene/Node node]
   (create-entity ctx parent-object node (portal? node)))
  ([^Context ctx ^three/Object3D parent ^vscene/Node node portal?]
   (let [{:keys [component-config]} (.-data node)
         obj (if portal?
               (resolve-portal-object parent node)
               (create-entity-object ctx node))]
     (when-not portal?
       (.add parent obj))
     (set! (.-threejs node) obj)
     (let [sctx ^SceneContext (active-scene-ctx ctx)]
       (registries/register-entity! (.-entityRegistry sctx) (.-id node) obj))
     (let [post-added-fns (on-entity-added ctx node obj component-config)]
       (.for-each-child node (partial create-entity ctx obj))
       (doseq [cb post-added-fns]
         (cb)))
     obj)))

(defn- destroy-entity
  ([^Context ctx ^vscene/Node node]
   (if (portal? node)
     (.for-each-child node (partial destroy-entity ctx))
     (let [{:keys [component-key
                   component-config]} (.-data node)
           entity-type (get (.-entityTypes ctx) component-key)
           obj  ^three/Object3D (.-threejs node)]
       (let [post-removed-fns (on-entity-removed ctx node obj component-config)]
         (.for-each-child node (partial destroy-entity ctx))
         (doseq [cb post-removed-fns]
           (cb))
         (when-let [parent (.-parent obj)]
           (.remove parent obj)))
       (let [sctx ^SceneContext (active-scene-ctx ctx)]
         (registries/unregister-entity! (.-entityRegistry sctx) (.-id node)))
       (entity/destroy! entity-type (.-context node) obj component-config)))))

(defn- update-entity
  [^Context ctx ^vscene/Node node _old-data new-data]
  (let [{:keys [component-config
                component-key
                position
                rotation
                scale
                visible]} new-data
        entity-type (get (.-entityTypes ctx) component-key)
        obj  ^three/Object3D (.-threejs node)]
    (entity/update! entity-type (.-context node) obj component-config)
    (threejs/set-position! obj position)
    (threejs/set-rotation! obj rotation)
    (threejs/set-scale! obj scale)
    (set! (.-visible obj) visible)
    (on-entity-updated ctx node obj component-config)
    obj))

(defn- transform-entity
  [^Context _ctx ^vscene/Node node]
  (let [{:keys [position
                rotation
                scale
                visible]} (.-data node)
        obj ^three/Object3D (.-threejs node)]
    (threejs/set-position! obj position)
    (threejs/set-rotation! obj rotation)
    (threejs/set-scale! obj scale)
    (set! (.-visible obj) visible)))

(defn- replace-entity
  "Destroy and recreate an entity at a given node in the scene-graph"
  [^Context ctx ^vscene/Node node old-data new-data]
  (let [old-obj (.-threejs node)
        {old-component-key :component-key
         old-component-config :component-config} old-data
        old-entity-type (get (.-entityTypes ctx) old-component-key)
        parent-obj (.-parent old-obj)
        children (.-children old-obj)]
    (on-entity-removed ctx node old-obj old-component-config)
    (entity/destroy! old-entity-type (.-context node) old-obj old-component-config)
    (let [new-obj (create-entity-object ctx node)]
      (set! (.-threejs node) new-obj)
      (.remove parent-obj old-obj)
      (.add parent-obj new-obj)
      (when-not (.terminal? node)
        (doseq [child (aclone children)]
          (.add new-obj child)))
      (let [sctx ^SceneContext (active-scene-ctx ctx)]
        (registries/register-entity! (.-entityRegistry sctx) (.-id node) new-obj))
      (on-entity-added ctx node new-obj (:component-config new-data)))))

(defn- init-scene! [^Context context virtual-scene scene-root]
  (create-entity context scene-root (.-root virtual-scene)))

;; ---------------------------------------------------------------------------
;; Update dispatch
;; ---------------------------------------------------------------------------

(defn- update-type [^Context context ^vscene/Node node o n]
  (cond
    (not= (:component-key o)
          (:component-key n))
    :replace-entity

    (not= (:component-config o)
          (:component-config n))
    (if (and (in-place-update? context node)
             (= (:id (:component-config o))
                (:id (:component-config n))))
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
                          (js/console.error "Failed to replace entity" ex
                                            (clj->js (.-data node)))))
      :update-entity (try
                       (update-entity context node old-data new-data)
                       (catch :default ex
                         (js/console.error "Failed to update entity" ex
                                           (clj->js (.-data node)))))
      :transform-entity (try
                          (transform-entity context node)
                          (catch :default ex
                            (js/console.error "Failed to transform entity" ex
                                              (clj->js (.-data node))))))))

;; ---------------------------------------------------------------------------
;; Frame pacing
;; ---------------------------------------------------------------------------

(defn- should-process-frame? [^Context context]
  (if-let [frame-interval (.-frameInterval context)]
    (let [now (js/performance.now)
          elapsed (- now (.-lastFrameTime context))]
      ;; Use a 1ms tolerance to avoid skipping frames due to vsync jitter
      ;; on high-refresh displays (e.g. 120Hz where 4 frames = 33.32ms < 33.33ms)
      (if (>= elapsed (- frame-interval 1.0))
        (do (set! (.-lastFrameTime context) now)
            true)
        false))
    true))

;; ---------------------------------------------------------------------------
;; Scene normalization helpers
;; ---------------------------------------------------------------------------

(defn- normalize-scenes
  "Normalize the root-fn argument into a map of {key -> root-fn}.
   A single function becomes {:default root-fn}."
  [root-fn]
  (if (map? root-fn)
    root-fn
    {:default root-fn}))

(defn- create-scene-context
  "Create a SceneContext for a single scene. virtualScene is set later."
  [key width height scene-opts]
  (let [camera (three/PerspectiveCamera. 75 (/ width height) 0.1 1000)
        cameras (array)
        scene-root (new three/Scene)]
    (when-let [bg (:background scene-opts)]
      (set! (.-background scene-root) (three/Color. bg)))
    (SceneContext. key nil scene-root camera cameras camera nil)))

(defn- clear-scene-ctx!
  "Clear all entities from a SceneContext. Sets activeSceneCtx before destroying."
  [^Context context ^SceneContext sctx]
  (set! (.-activeSceneCtx context) sctx)
  (let [vscene-root ^vscene/Node (.-root (.-virtualScene sctx))]
    (.for-each-child vscene-root (partial destroy-entity context)))
  (.clear (.-sceneRoot sctx)))

;; ---------------------------------------------------------------------------
;; RenderPipeline compositing
;; ---------------------------------------------------------------------------

(defn- rebuild-pipeline!
  "Create/recreate the RenderPipeline by calling the user's :render-pipeline fn
   with pass() nodes for each scene."
  [^Context context]
  (let [pipeline-fn (.-renderPipelineFn context)
        renderer (.-renderer context)
        scenes (.-scenes context)
        render-order (.-renderOrder context)
        pass-nodes (reduce (fn [m scene-key]
                             (let [sctx ^SceneContext (get scenes scene-key)
                                   pass-node (pass (.-sceneRoot sctx) (.-camera sctx))]
                               (set! (.-lastPipelineCamera sctx) (.-camera sctx))
                               (assoc m scene-key pass-node)))
                           {}
                           render-order)
        output-node (pipeline-fn pass-nodes)]
    (when-let [old-pipeline (.-renderPipeline context)]
      (.dispose old-pipeline))
    (let [pipeline (three/RenderPipeline. renderer)]
      (set! (.-outputNode pipeline) output-node)
      (set! (.-renderPipeline context) pipeline))))

(defn- render-with-pipeline!
  "Render using the RenderPipeline. Rebuilds if cameras changed or pipeline is nil."
  [^Context context delta-time]
  (let [scenes (.-scenes context)
        render-order (.-renderOrder context)
        camera-changed? (some (fn [scene-key]
                                (let [sctx ^SceneContext (get scenes scene-key)]
                                  (not (identical? (.-camera sctx)
                                                   (.-lastPipelineCamera sctx)))))
                              render-order)]
    (when (or camera-changed? (nil? (.-renderPipeline context)))
      (rebuild-pipeline! context))
    (.render ^js (.-renderPipeline context) delta-time)))

;; ---------------------------------------------------------------------------
;; Rendering dispatch
;; ---------------------------------------------------------------------------

(defn- render-scenes!
  "Render all scenes. Dispatches to RenderPipeline or sequential rendering."
  [^Context context delta-time]
  (let [renderer (.-renderer context)
        scenes (.-scenes context)
        render-order (.-renderOrder context)]
    (if (.-renderPipelineFn context)
      ;; RenderPipeline mode
      (render-with-pipeline! context delta-time)
      ;; Sequential rendering mode
      (if (= 1 (count render-order))
        ;; Single scene — simple render (backwards compatible path)
        (let [sctx ^SceneContext (get scenes (first render-order))]
          (.render renderer (.-sceneRoot sctx) (.-camera sctx)))
        ;; Multi scene — sequential with depth clearing
        (let [scene-opts (.-sceneOpts context)]
          (doseq [[idx scene-key] (map-indexed vector render-order)]
            (let [sctx ^SceneContext (get scenes scene-key)
                  sopts (get scene-opts scene-key {})]
              (if (= 0 idx)
                (do
                  (set! (.-autoClear renderer) true)
                  (.render renderer (.-sceneRoot sctx) (.-camera sctx)))
                (do
                  (set! (.-autoClear renderer) false)
                  (when (get sopts :clear-depth true)
                    (.clearDepth renderer))
                  (when (:clear-color sopts)
                    (.clearColor renderer))
                  (.render renderer (.-sceneRoot sctx) (.-camera sctx))))))
          ;; Restore autoClear
          (set! (.-autoClear renderer) true))))))

;; ---------------------------------------------------------------------------
;; Animation loop
;; ---------------------------------------------------------------------------

(defn- animate [^Context context]
  (let [pacer (.-framePacer context)
        now (js/performance.now)]
    (when (if pacer
            (frame-pacer/should-render? pacer now)
            (should-process-frame? context))
      (let [render-start now
            stats ^js (.-stats context)
            clock ^three/Clock (.-clock context)
            renderer (.-renderer context)
            before-render-cb (.-beforeRenderCb context)
            after-render-cb (.-afterRenderCb context)
            scenes (.-scenes context)
            render-order (.-renderOrder context)]
        (when stats
          (.begin stats))
        (let [delta-time (.getDelta clock)]
          (systems/dispatch-on-tick context delta-time)
          ;; Invoke before-render callback
          (when before-render-cb (before-render-cb delta-time))
          ;; Process each scene's virtual tree
          (doseq [scene-key render-order]
            (let [sctx ^SceneContext (get scenes scene-key)
                  virtual-scene ^vscene/Scene (.-virtualScene sctx)
                  changelog (array)]
              (set! (.-activeSceneCtx context) sctx)
              (vscene/render! virtual-scene changelog)
              (doseq [change changelog]
                (apply-change! context change))))
          ;; Render all scenes
          (render-scenes! context delta-time)
          ;; Invoke after-render callback
          (when after-render-cb (after-render-cb delta-time)))
        (when stats
          (.end stats))
        ;; Record render time for frame pacer
        (when pacer
          (frame-pacer/record-render-time! pacer (- (js/performance.now) render-start)))))))

;; ---------------------------------------------------------------------------
;; Canvas / renderer helpers
;; ---------------------------------------------------------------------------

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

;; ---------------------------------------------------------------------------
;; Context creation
;; ---------------------------------------------------------------------------

(defn- ^Context create-context [root-fn dom-root {:keys [on-before-render
                                                         on-after-render
                                                         entity-registry
                                                         shadow-map
                                                         systems
                                                         entity-types
                                                         target-framerate
                                                         auto-frame-pacing
                                                         render-order
                                                         render-pipeline
                                                         scenes]}]
  (let [canvas (get-canvas dom-root)
        width (.-offsetWidth canvas)
        height (.-offsetHeight canvas)
        renderer (new three/WebGPURenderer (clj->js {:canvas canvas}))
        clock (new three/Clock)
        frame-interval (when (and target-framerate (not auto-frame-pacing))
                         (* 1000.0 (/ 1.0 target-framerate)))
        scenes-map (normalize-scenes root-fn)
        multi-scene? (> (count scenes-map) 1)
        scene-keys (or render-order (vec (keys scenes-map)))
        primary-key (first scene-keys)
        scene-opts (or scenes {})]
    (.setSize renderer width height)
    (set-shadow-map! renderer shadow-map)
    ;; Create SceneContexts
    (let [scene-ctxs (reduce (fn [m k]
                               (let [sctx (create-scene-context k width height (get scene-opts k))]
                                 (set! (.-entityRegistry sctx)
                                       (or entity-registry (atom {})))
                                 (assoc m k sctx)))
                             {}
                             scene-keys)
          primary-sctx ^SceneContext (get scene-ctxs primary-key)
          [normalized-systems sorted-sys-keys] (systems/normalize-systems systems)
          context (Context. dom-root frame-interval
                            canvas clock renderer
                            on-before-render
                            on-after-render
                            (merge builtin-entity-types entity-types)
                            normalized-systems
                            scene-ctxs
                            scene-keys
                            nil
                            primary-key)]
      ;; Store sorted system keys, per-scene opts, and render-pipeline fn
      (set! (.-systemKeys context) sorted-sys-keys)
      (set! (.-sceneOpts context) scene-opts)
      (when render-pipeline
        (set! (.-renderPipelineFn context) render-pipeline))
      ;; Systems are initialized before first virtual-render
      (systems/dispatch-init normalized-systems
                             sorted-sys-keys
                             {:threejs-renderer renderer
                              :threejs-scene (.-sceneRoot primary-sctx)
                              :threejs-default-camera (.-defaultCamera primary-sctx)
                              :entity-registry (.-entityRegistry primary-sctx)
                              :canvas canvas
                              :threejs-scenes (reduce-kv (fn [m k ^SceneContext sctx]
                                                           (assoc m k (.-sceneRoot sctx)))
                                                         {} scene-ctxs)})
      ;; Create virtual scenes and init each scene
      (doseq [k scene-keys]
        (let [sctx ^SceneContext (get scene-ctxs k)
              root-fn-for-scene (get scenes-map k)
              initial-ctx (if multi-scene?
                            {:threeagent/scene-key k}
                            {})
              virtual-scene (vscene/create root-fn-for-scene initial-ctx)]
          (set! (.-virtualScene sctx) virtual-scene)
          (set! (.-activeSceneCtx context) sctx)
          (init-scene! context virtual-scene (.-sceneRoot sctx))))
      ;; Frame pacing
      (when auto-frame-pacing
        (set! (.-framePacer context) (frame-pacer/create target-framerate nil)))
      (when frame-interval
        (set! (.-lastFrameTime context) (js/performance.now)))
      (.push contexts context)
      (.setAnimationLoop renderer #(animate context))
      context)))

;; ---------------------------------------------------------------------------
;; Context reset (hot reload)
;; ---------------------------------------------------------------------------

(defn- reset-context! [^Context old-context root-fn {:keys [on-before-render on-after-render shadow-map
                                                            entity-types systems entity-registry
                                                            target-framerate auto-frame-pacing
                                                            render-order render-pipeline scenes]}]
  (let [renderer ^js (.-renderer old-context)
        old-scenes (.-scenes old-context)]
    ;; Dispatch destroy for systems
    (systems/dispatch-destroy (.-systems old-context)
                              (.-systemKeys old-context)
                              (raw-context->context old-context))
    ;; Clear and destroy all existing scenes
    (doseq [[_ sctx] old-scenes]
      (clear-scene-ctx! old-context sctx)
      (vscene/destroy! (.-virtualScene ^SceneContext sctx)))
    ;; Dispose old RenderPipeline
    (when-let [pipeline (.-renderPipeline old-context)]
      (.dispose pipeline)
      (set! (.-renderPipeline old-context) nil))
    ;; Set up new scenes
    (let [scenes-map (normalize-scenes root-fn)
          multi-scene? (> (count scenes-map) 1)
          scene-keys (or render-order (vec (keys scenes-map)))
          primary-key (first scene-keys)
          scene-opts (or scenes {})
          width (.-offsetWidth (.-canvas old-context))
          height (.-offsetHeight (.-canvas old-context))]
      (set-shadow-map! renderer shadow-map)
      (let [[normalized-systems sorted-sys-keys] (systems/normalize-systems systems)]
        (set! (.-systems old-context) normalized-systems)
        (set! (.-systemKeys old-context) sorted-sys-keys)
        (set! (.-entityTypes old-context) (merge builtin-entity-types entity-types))
        (set! (.-renderOrder old-context) scene-keys)
        (set! (.-primarySceneKey old-context) primary-key)
        (set! (.-sceneOpts old-context) scene-opts)
        (set! (.-renderPipelineFn old-context) render-pipeline)
        ;; Create new SceneContexts
        (let [scene-ctxs (reduce (fn [m k]
                                   (let [sctx (create-scene-context k width height (get scene-opts k))]
                                     (set! (.-entityRegistry sctx)
                                           (or entity-registry (atom {})))
                                     (when entity-registry
                                       (registries/reset-registry! entity-registry))
                                     (assoc m k sctx)))
                                 {}
                                 scene-keys)
              primary-sctx ^SceneContext (get scene-ctxs primary-key)]
          (set! (.-scenes old-context) scene-ctxs)
          ;; Systems init
          (systems/dispatch-init normalized-systems
                                 sorted-sys-keys
                                 {:threejs-renderer renderer
                                  :threejs-scene (.-sceneRoot primary-sctx)
                                  :threejs-default-camera (.-defaultCamera primary-sctx)
                                  :entity-registry (.-entityRegistry primary-sctx)
                                  :canvas (.-canvas old-context)
                                  :threejs-scenes (reduce-kv (fn [m k ^SceneContext sctx]
                                                               (assoc m k (.-sceneRoot sctx)))
                                                             {} scene-ctxs)})
        ;; Create virtual scenes and init each scene
        (doseq [k scene-keys]
          (let [sctx ^SceneContext (get scene-ctxs k)
                root-fn-for-scene (get scenes-map k)
                initial-ctx (if multi-scene?
                              {:threeagent/scene-key k}
                              {})
                virtual-scene (vscene/create root-fn-for-scene initial-ctx)]
            (set! (.-virtualScene sctx) virtual-scene)
            (set! (.-activeSceneCtx old-context) sctx)
            (init-scene! old-context virtual-scene (.-sceneRoot sctx)))))
      ;; Callbacks and frame pacing
      (set! (.-beforeRenderCb old-context) on-before-render)
      (set! (.-afterRenderCb old-context) on-after-render)
      (if auto-frame-pacing
        (do
          (set! (.-frameInterval old-context) nil)
          (if-let [pacer (.-framePacer old-context)]
            (frame-pacer/reset-pacer! pacer target-framerate nil)
            (set! (.-framePacer old-context) (frame-pacer/create target-framerate nil))))
        (do
          (set! (.-framePacer old-context) nil)
          (let [frame-interval (when target-framerate
                                 (* 1000.0 (/ 1.0 target-framerate)))]
            (set! (.-frameInterval old-context) frame-interval)
            (when frame-interval
              (set! (.-lastFrameTime old-context) (js/performance.now))))))
      old-context))))

;; ---------------------------------------------------------------------------
;; Public API
;; ---------------------------------------------------------------------------

(defn- find-context [dom-root]
  (first (filter #(= (.-domRoot ^js %) dom-root) contexts)))

(defn- create-or-reset-context [root-fn dom-root config]
  (if-let [existing-context (find-context dom-root)]
    (reset-context! existing-context root-fn config)
    (create-context root-fn dom-root config)))

(defn render [root-fn dom-root config]
  (raw-context->context (create-or-reset-context root-fn dom-root config)))
