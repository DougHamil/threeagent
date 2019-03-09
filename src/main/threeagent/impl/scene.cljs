(ns threeagent.impl.scene
  (:require [threeagent.impl.virtual-scene :as vscene]
            [threeagent.impl.util :refer [log]]
            [threeagent.impl.threejs :as threejs]
            [threeagent.impl.component :refer [render-component]]
            ["three" :as three]
            [cljs.core :refer [exists?]]))

(defn- create-object [node-data]
  (let [comp-config (:component-config node-data)
        obj (render-component (:component-key node-data) comp-config)]
    (threejs/set-position! obj (:position node-data))
    (threejs/set-rotation! obj (:rotation node-data))
    (threejs/set-scale! obj (:scale node-data))
    obj))

(defn- set-node-object [^js context ^vscene/Node node node-data obj]
  (set! (.-threejs node) obj)
  (when (= :camera (:component-key node-data))
    (set! (.-camera context) obj)))

(defn- add-node [context parent-object ^vscene/Node node]
  (try
    (let [node-data (.-data node)
          comp-config (:component-config node-data)
          obj (create-object node-data)]
      (set-node-object context node node-data obj)
      (.add parent-object obj)
      (.for-each-child node (partial add-node context obj))
      (when-let [callback (:on-added (.-meta node))]
        (callback obj))
      obj)
    (catch :default e
      (log "Failed to add node")
      (log e)
      (println node))))

(defn- remove-node! [^vscene/Node node]
  (let [obj (.-threejs node)
        parent-obj ^js (.-threejs (.-parent node))]
    (when-let [callback (:on-removed (.-meta node))]
      (callback obj))
    (.remove parent-obj obj)
    (.for-each-child node remove-node!)))

(defn- init-scene [context virtual-scene scene-root]
  (add-node context scene-root (.-root virtual-scene)))

(defn diff-data [o n]
  (let [this (when (or (not= (:component-config o) (:component-config n))
                       (not= (:component-key o) (:component-key n)))
               [o n])
        position (when (not= (:position o) (:position n)) (:position n))
        rotation (when (not= (:rotation o) (:rotation n)) (:rotation n))
        scale (when (not= (:scale o) (:scale n)) (:scale n))]
    {:this this
     :scale scale
     :position position
     :rotation rotation}))

(defn- update-node! [context node old-data new-data]
  (let [diff (diff-data old-data new-data)
        old-obj ^js (.-threejs node)
        metadata (.-meta node)
        this (:this diff)]
    (if this
      ;; Fully reconstruct scene object
      (try
        (let [[o n] this
              parent-obj (.-parent old-obj)
              children (.-children old-obj)
              new-obj (create-object new-data)]
          (when-let [callback (:on-removed metadata)]
            (callback old-obj))
          (set-node-object context node new-data new-obj)
          (.remove parent-obj old-obj)
          (.add parent-obj new-obj)
          (doseq [child children]
            (.add new-obj child))
          (when-let [callback (:on-added metadata)]
            (callback new-obj)))
        (catch :default ex
          (log "Failed to update node due to error")
          (log ex)
          (println node)
          (println new-data)
          (println old-data)
          (log node)))
      ;; Update transformations
      (do
        (when (:position diff) (threejs/set-position! old-obj (:position diff)))
        (when (:rotation diff) (threejs/set-rotation! old-obj (:rotation diff)))
        (when (:scale diff) (threejs/set-scale! old-obj (:scale diff)))))))

(defn- apply-change! [^js context [^vscene/Node node action old new]]
  (cond
    (= :add action)
    (add-node context ^js (.-threejs (.-parent node)) node)

    (= :remove action)
    (remove-node! node)

    (= :update action)
    (update-node! context node old new)))

(defn- apply-virtual-scene-changes! [context changelog]
  (doseq [change changelog]
    (apply-change! context change)))

(defn- animate [^js context]
  (let [stats (.-stats context)
        clock (.-clock context)
        virtual-scene ^vscene/Scene (.-virtualScene context)
        renderer (.-renderer context)
        composer (.-composer context)
        camera (.-camera context)
        scene-root (.-sceneRoot context)
        before-render-cb (.-beforeRenderCb context)]
    ;(log camera)
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
      ;; Render ThreeJS Scene
      (if composer
        (.render composer delta-time)
        (.render renderer scene-root camera)))
    (when stats
      (.end stats))))

(defn- get-canvas [dom-root]
  (if (= "canvas" (clojure.string/lower-case (.-tagName dom-root)))
    dom-root
    (let [c (.createElement js/document "canvas")]
      (.appendChild dom-root c))))

(defn- create-camera [width height ortho?]
  (if ortho?
    (three/OrthographicCamera. (/ width -2.0
                                  (/ width 2.0)
                                  (/ height 2.0)
                                  (/ height -2.0)
                                  0.1
                                  1000))
    (three/PerspectiveCamera. 75 (/ width height) 0.1 1000)))
         
(defn- create-context [root-fn dom-root on-before-render-cb ortho-camera? renderer-opts]
  (let [canvas (get-canvas dom-root)
        width (.-offsetWidth canvas)
        height (.-offsetHeight canvas)
        virtual-scene (vscene/create root-fn)
        renderer (new three/WebGLRenderer (clj->js (merge renderer-opts
                                                             {:canvas canvas})))
        camera (create-camera width height ortho-camera?)
        scene-root (new three/Scene)]
      (.setSize renderer width height)
      (let [context ^js (clj->js {:sceneRoot scene-root
                                  :canvas canvas
                                  :camera camera
                                  :beforeRenderCb on-before-render-cb
                                  :stats (when (exists? js/Stats)
                                          (js/Stats.))
                                  :clock (three/Clock.)
                                  :renderer renderer
                                  :virtualScene virtual-scene})]
        (init-scene context virtual-scene scene-root)
        (when (.-stats context)
          (.showPanel (.-stats context) 1)
          (.appendChild js/document.body (.-dom (.-stats context))))
        (set! (.-animateFn context) #(animate context))
        context)))

(defn- remove-all-children! [^vscene/Node vscene-root]
  (.for-each-child vscene-root remove-node!))

(defn reset-scene! [^js scene root-fn {:keys [on-before-render]}]
  (let [root ^js (.-sceneRoot scene)
        virtual-scene ^vscene/Scene (.-virtualScene scene)
        new-virtual-scene (vscene/create root-fn)]
    (remove-all-children! (.-root virtual-scene))
    (vscene/destroy! virtual-scene)
    (init-scene scene new-virtual-scene root)
    (set! (.-virtualScene scene) new-virtual-scene)
    (set! (.-beforeRenderCb scene) on-before-render)
    scene))

(defn render [root-fn dom-root {:keys [on-before-render ortho-camera?
                                       renderer-opts]}]
  (let [context (create-context root-fn dom-root on-before-render ortho-camera? renderer-opts)
        renderer ^js (.-renderer context)]
    (.setAnimationLoop renderer (.-animateFn context))
    context))
