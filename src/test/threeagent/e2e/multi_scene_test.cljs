(ns threeagent.e2e.multi-scene-test
  (:require [cljs.test :refer-macros [deftest is use-fixtures]]
            ["three/webgpu" :as three]
            [threeagent.e2e.fixture :as fixture]
            [threeagent.threejs.util :refer [child-count]]
            [threeagent.entity :refer [IEntityType]]
            [threeagent.core :as th]))

(defonce canvas (atom nil))

(use-fixtures :each (fixture/with-canvas canvas))

;; Entity type that logs creation context and destruction
(deftype ContextTracker [log]
  IEntityType
  (create [_ ctx {:keys [test-id]}]
    (let [obj (three/Object3D.)]
      (swap! log conj {:event :created :test-id test-id :context ctx})
      obj))
  (destroy! [_ _ _ {:keys [test-id]}]
    (swap! log conj {:event :destroyed :test-id test-id})))

;; ---------------------------------------------------------------------------
;; Context map
;; ---------------------------------------------------------------------------

(deftest multi-scene-context-map-test
  (let [world-fn (fn [] [:object])
        ui-fn (fn [] [:object])
        ctx (th/render {:world world-fn :ui ui-fn} @canvas
                       {:render-order [:world :ui]})]
    ;; Primary scene keys (backwards compatible)
    (is (some? (:threejs-renderer ctx)))
    (is (some? (:threejs-scene ctx)))
    (is (some? (:threejs-default-camera ctx)))
    ;; Multi-scene keys
    (is (= #{:world :ui} (set (keys (:threejs-scenes ctx)))))
    (is (= #{:world :ui} (set (keys (:scene-cameras ctx)))))
    ;; Primary scene is first in render-order (:world)
    (is (= (get (:threejs-scenes ctx) :world) (:threejs-scene ctx)))
    ;; Scenes are distinct Three.js Scene objects
    (is (not= (get (:threejs-scenes ctx) :world)
              (get (:threejs-scenes ctx) :ui)))))

;; ---------------------------------------------------------------------------
;; Independent entity trees
;; ---------------------------------------------------------------------------

(deftest multi-scene-independent-entity-trees-test
  (let [world-fn (fn [] [:object
                          [:box]
                          [:sphere]])
        ui-fn (fn [] [:object
                       [:box]])
        ctx (th/render {:world world-fn :ui ui-fn} @canvas
                       {:render-order [:world :ui]})
        world-scene (get (:threejs-scenes ctx) :world)
        ui-scene (get (:threejs-scenes ctx) :ui)]
    ;; Each scene root has one child (the root :object wrapper entity)
    (is (= 1 (child-count world-scene)))
    (is (= 1 (child-count ui-scene)))
    ;; World: Scene → root-object → user-object → [box, sphere]
    (let [world-root (aget (.-children world-scene) 0)
          world-user (aget (.-children world-root) 0)]
      (is (= 2 (child-count world-user))))
    ;; UI: Scene → root-object → user-object → [box]
    (let [ui-root (aget (.-children ui-scene) 0)
          ui-user (aget (.-children ui-root) 0)]
      (is (= 1 (child-count ui-user))))))

;; ---------------------------------------------------------------------------
;; Scene key in entity context
;; ---------------------------------------------------------------------------

(deftest multi-scene-scene-key-context-test
  (let [log (atom [])
        entity-types {:tracker (ContextTracker. log)}
        world-fn (fn [] [:tracker {:test-id "w1"}])
        ui-fn (fn [] [:tracker {:test-id "u1"}])]
    (th/render {:world world-fn :ui ui-fn} @canvas
               {:render-order [:world :ui]
                :entity-types entity-types})
    (let [created (filter #(= :created (:event %)) @log)
          by-id (into {} (map (fn [{:keys [test-id context]}]
                                [test-id context])
                              created))]
      ;; Each scene's entities receive :threeagent/scene-key in their context
      (is (= :world (:threeagent/scene-key (get by-id "w1"))))
      (is (= :ui (:threeagent/scene-key (get by-id "u1")))))))

;; ---------------------------------------------------------------------------
;; Reactive isolation
;; ---------------------------------------------------------------------------

(deftest multi-scene-reactive-test
  (let [log (atom [])
        entity-types {:tracker (ContextTracker. log)}
        world-state (th/atom true)
        world-fn (fn []
                   [:object
                    (when @world-state
                      [:tracker {:test-id "w1"}])])
        ui-fn (fn [] [:tracker {:test-id "u1"}])]
    (fixture/async-run!
     [{:when (fn []
               (th/render {:world world-fn :ui ui-fn} @canvas
                          {:render-order [:world :ui]
                           :entity-types entity-types}))
       :then (fn []
               (let [created (filter #(= :created (:event %)) @log)]
                 (is (= #{"w1" "u1"} (set (map :test-id created))))))}
      {:when (fn []
               (reset! log [])
               (reset! world-state false))
       :then (fn []
               ;; Only w1 should be destroyed, u1 stays unaffected
               (let [destroyed (filter #(= :destroyed (:event %)) @log)]
                 (is (= 1 (count destroyed)))
                 (is (= "w1" (:test-id (first destroyed))))))}])))

;; ---------------------------------------------------------------------------
;; Hot reload: single scene → multi scene
;; ---------------------------------------------------------------------------

(deftest multi-scene-hot-reload-test
  (let [log (atom [])
        entity-types {:tracker (ContextTracker. log)}
        single-fn (fn [] [:tracker {:test-id "s1"}])
        world-fn (fn [] [:tracker {:test-id "w1"}])
        ui-fn (fn [] [:tracker {:test-id "u1"}])]
    (fixture/async-run!
     [{:when (fn []
               (th/render single-fn @canvas
                          {:entity-types entity-types}))
       :then (fn []
               (let [created (filter #(= :created (:event %)) @log)]
                 (is (= #{"s1"} (set (map :test-id created))))))}
      {:when (fn []
               (reset! log [])
               ;; Hot reload from single to multi-scene
               (th/render {:world world-fn :ui ui-fn} @canvas
                          {:render-order [:world :ui]
                           :entity-types entity-types}))
       :then (fn []
               ;; s1 should be destroyed, w1 and u1 created
               (let [destroyed (filter #(= :destroyed (:event %)) @log)
                     created (filter #(= :created (:event %)) @log)]
                 (is (= #{"s1"} (set (map :test-id destroyed))))
                 (is (= #{"w1" "u1"} (set (map :test-id created))))))}])))
