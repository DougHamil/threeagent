(ns threeagent.impl.scene
  (:require [threeagent.impl.virtual-scene :as vscene]
            [threeagent.impl.util :refer [log]]
            [threeagent.impl.threejs :as threejs]
            [threeagent.impl.component :refer [render-component]]
            [threeagent.impl.types :refer [Context]]
            [threeagent.impl.system :as systems]
            [clojure.string :as string]
            ["three" :as three]))

(defonce ^:private contexts (array))

(defn- create-object [node-data]
  (let [comp-config (:component-config node-data)
        obj (render-component (:component-key node-data) comp-config)]
    (threejs/set-position! obj (:position node-data))
    (threejs/set-rotation! obj (:rotation node-data))
    (threejs/set-scale! obj (:scale node-data))
    (when (:cast-shadow node-data) (threejs/set-cast-shadow! obj (:cast-shadow node-data)))
    (when (:receive-shadow node-data) (threejs/set-receive-shadow! obj (:receive-shadow node-data)))
    obj))

(defn- set-node-object [^Context context ^vscene/Node node obj]
  (set! (.-threejs node) obj)
  (when (.-isCamera obj)
    (.push (.-cameras context) obj)))

(defn- add-node [^Context context parent-object ^vscene/Node node]
  (try
    (let [node-data (.-data node)
          comp-config (:component-config node-data)
          obj (create-object node-data)]
      (set-node-object context node obj)
      (.add parent-object obj)
      (.for-each-child node (partial add-node context obj))
      (.dispatchEvent obj #js {:type "on-added"})
      (when-let [callback (:on-added (.-meta node))]
        (callback obj))
      (systems/dispatch-on-added context (.-id obj) obj comp-config)
      obj)
    (catch :default e
      (js/console.error "Failed to add node:")
      (js/console.error (clj->js {:node node :error e})))))

(defn- on-object-removed [^Context context ^vscene/Node node ^js obj]
  (.dispatchEvent obj #js {:type "on-removed"})
  (when-let [callback (:on-removed (.-meta node))]
    (callback obj))
  (systems/dispatch-on-removed context (.-id obj) obj (:component-config (.-data node)))
  (when (.-isCamera obj)
    (let [cams (.-cameras context)]
      (.splice cams (.indexOf cams obj) 1))))

(defn- remove-node! [^Context context ^vscene/Node node]
  (let [obj (.-threejs node)
        parent-obj ^js (.-threejs (.-parent node))]
    (on-object-removed context node obj)
    (.remove parent-obj obj)
    (.for-each-child node (partial remove-node! context))))

(defn- init-scene [^Context context virtual-scene scene-root]
  (add-node context scene-root (.-root virtual-scene)))

(defn diff-data [o n]
  (let [this (when (or (not= (:component-config o) (:component-config n))
                       (not= (:component-key o) (:component-key n)))
               [o n])
        position (when (not= (:position o) (:position n)) (:position n))
        rotation (when (not= (:rotation o) (:rotation n)) (:rotation n))
        scale (when (not= (:scale o) (:scale n)) (:scale n))
        receive-shadow (when (not= (:receive-shadow o) (:receive-shadow n)) (:receive-shadow n))
        cast-shadow (when (not= (:cast-shadow o) (:cast-shadow n)) (:cast-shadow n))]
    {:this this
     :scale scale
     :position position
     :rotation rotation
     :cast-shadow cast-shadow
     :receive-shadow receive-shadow}))

(defn- update-node! [^Context context ^vscene/Node node old-data new-data]
  (let [diff (diff-data old-data new-data)
        old-obj ^js (.-threejs node)
        metadata (.-meta node)
        this (:this diff)]
    (if this
      ;; Fully reconstruct scene object
      (try
        (let [parent-obj (.-parent old-obj)
              children (.-children old-obj)
              new-obj (create-object new-data)]
          (on-object-removed context node old-obj)
          (set-node-object context node new-obj)
          (.remove parent-obj old-obj)
          (.add parent-obj new-obj)
          (when-not (.terminal? node)
            (doseq [child children]
              (.add new-obj child)))
          (.dispatchEvent new-obj #js {:type "on-added"})
          (when-let [callback (:on-added metadata)]
            (callback new-obj))
          (systems/dispatch-on-added context (.-id new-obj) new-obj (:component-config new-data)))
        (catch :default ex
          (log "Failed to update node due to error")
          (log ex)
          (log node)))
      ;; Update transformations
      (do
        (when (:position diff) (threejs/set-position! old-obj (:position diff)))
        (when (:rotation diff) (threejs/set-rotation! old-obj (:rotation diff)))
        (when (:scale diff) (threejs/set-scale! old-obj (:scale diff)))
        (when (:cast-shadow diff) (threejs/set-cast-shadow! old-obj (:cast-shadow diff)))
        (when (:receive-shadow diff) (threejs/set-receive-shadow! old-obj (:receive-shadow diff)))))))

(defn- apply-change! [^Context context [^vscene/Node node action old new]]
  (cond
    (= :add action)
    (add-node context ^js (.-threejs (.-parent node)) node)

    (= :remove action)
    (remove-node! context node)

    (= :update action)
    (update-node! context node old new)))

(defn- apply-virtual-scene-changes! [^Context context changelog]
  (doseq [change changelog]
    (apply-change! context change)))

(defn- find-active-camera [^Context context]
  (or
   (->> (.-cameras context)
        (filter #(.-active %))
        (last))
   (.-camera context)))

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
      ;; Invoke callbacks
      (when before-render-cb (before-render-cb delta-time))
      ;; Render virtual scene
      (vscene/render! virtual-scene changelog)
      ;; Apply virtual scene changes to ThreeJs scene
      (apply-virtual-scene-changes! context changelog)
      ;; Fetch camera after applying the scene changes since it might have been updated
      (let [camera (find-active-camera context)]
        ;; Render ThreeJS Scene
        (if composer
          (.render composer delta-time)
          (.render renderer scene-root camera)))
      (when after-render-cb (after-render-cb delta-time))
      (systems/dispatch-on-tick context delta-time))
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
                                                         systems]}]
  (let [canvas (get-canvas dom-root)
        width (.-offsetWidth canvas)
        height (.-offsetHeight canvas)
        virtual-scene (vscene/create root-fn)
        renderer (new three/WebGLRenderer (clj->js {:canvas canvas}))
        camera (three/PerspectiveCamera. 75 (/ width height) 0.1 1000)
        cameras (array)
        scene-root (new three/Scene)
        clock (new three/Clock)]
    (.setSize renderer width height)
    (set-shadow-map! renderer shadow-map)
    (let [context (Context. virtual-scene
                                 scene-root
                                 dom-root nil
                                 canvas camera cameras
                                 clock renderer
                                 on-before-render
                                 on-after-render
                                 systems)]
      (set! (.-animateFn context) #(animate context))
      (init-scene context virtual-scene scene-root)
      (.push contexts context)
      (.setAnimationLoop renderer (.-animateFn context))
      context)))

(defn- remove-all-children! [^Context context ^vscene/Node vscene-root]
  (.for-each-child vscene-root (partial remove-node! context)))

(defn- raw-context->context [^Context raw-ctx]
  {:threejs-renderer (.-renderer raw-ctx)
   :threejs-scene (.-sceneRoot raw-ctx)
   :canvas (.-canvas raw-ctx)})

(defn- reset-context! [^Context context root-fn {:keys [on-before-render on-after-render shadow-map systems]}]
  (let [scene-root        ^js (.-sceneRoot context)
        virtual-scene     ^vscene/Scene (.-virtualScene context)
        new-virtual-scene (vscene/create root-fn)
        renderer          ^js (.-renderer context)
        old-context       (raw-context->context context)]
    (systems/dispatch-destroy (.-systems context) old-context)
    (remove-all-children! context (.-root virtual-scene))
    (vscene/destroy! virtual-scene)
    (set-shadow-map! renderer shadow-map)
    (set! (.-cameras context) (array))
    (set! (.-systems context) systems)
    (init-scene context new-virtual-scene scene-root)
    (set! (.-virtualScene context) new-virtual-scene)
    (set! (.-beforeRenderCb context) on-before-render)
    (set! (.-afterRenderCb context) on-after-render)
    context))

(defn- find-context [dom-root]
  (first (filter #(= (.-domRoot ^js %) dom-root) contexts)))

(defn- create-or-reset-context [root-fn dom-root config]
  (if-let [existing-context (find-context dom-root)]
    (reset-context! existing-context root-fn config)
    (create-context root-fn dom-root config)))


(defn render [root-fn dom-root config]
  (let [raw-ctx ^Context (create-or-reset-context root-fn dom-root config)
        ctx     (raw-context->context raw-ctx)]
    (systems/dispatch-init (.-systems raw-ctx) ctx)))
