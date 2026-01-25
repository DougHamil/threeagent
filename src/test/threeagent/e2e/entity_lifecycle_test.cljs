(ns threeagent.e2e.entity-lifecycle-test
  (:require [cljs.test :refer-macros [deftest is use-fixtures]]
            ["three/webgpu" :as three]
            [threeagent.e2e.fixture :as fixture]
            [threeagent.entity :refer [IEntityType IUpdateableEntityType]]
            [threeagent.core :as th]))

(defonce canvas (atom nil))

(use-fixtures :each (fixture/with-canvas canvas))

(deftype LoggingEntityType [log]
  IEntityType
  (create [_ _ {:keys [test-id]}]
    (let [obj (three/Object3D.)]
      (swap! log conj {:event :created
                       :id test-id})
      obj))
  (destroy! [_ _ _ {:keys [test-id]}]
    (swap! log conj {:event :destroyed
                     :id test-id})))

(deftype MyEntityType [state]
  IEntityType
  (create [_ {:keys [id]} {:keys [test-id]}]
    (let [obj (three/Object3D.)]
      (set! (.-testId obj) test-id)
      (set! (.-contextId obj) id)
      (swap! state assoc test-id obj)
      obj))
  (destroy! [_ _ ^three/Object3D obj _]
    (swap! state dissoc (.-testId obj))))

(deftype MyUpdateableEntityType [state]
  IEntityType
  (create [_ _ {:keys [test-id]}]
    (let [obj (three/Object3D.)]
      (set! (.-testId obj) test-id)
      (reset! state obj)
      obj))
  (destroy! [_ _ _ _]
    (reset! state nil))
  IUpdateableEntityType
  (update! [_ _ ^three/Object3D obj {:keys [test-id]}]
    (set! (.-testId obj) test-id)))

(deftest entity-type-test
  (let [state (atom {})
        entity-types {:my-entity (MyEntityType. state)}
        root-fn (fn []
                  [{:id "context"}
                   [:my-entity {:test-id "a"}
                    [{:id "context2"}
                     [:my-entity {:test-id "b"}]]]])]
    (th/render root-fn @canvas
               {:entity-types entity-types})
    (is (= #{"a" "b"} (set (keys @state))))
    (is (= "a" (.-testId ^js (get @state "a"))))
    (is (= "context" (.-contextId ^js (get @state "a"))))
    (is (= "b" (.-testId ^js (get @state "b"))))
    (is (= "context2" (.-contextId ^js (get @state "b"))))))

(deftest swap-entity-type-test
  (let [log (atom [])
        entity-types {:my-entity (LoggingEntityType. log)}
        state (th/atom true)
        root-fn (fn []
                  [:object
                   (if @state
                     [:my-entity {:test-id "a"}]
                     [:my-entity {:test-id "b"}])])]
    (fixture/async-run!
     [{:when (fn []
               (th/render root-fn @canvas {:entity-types entity-types}))
       :then (fn []
               (is (= [{:event :created
                        :id "a"}]
                      @log)))}
      {:when (fn []
               (reset! log [])
               (reset! state false))
       :then (fn []
               (is (= [{:event :destroyed
                        :id "a"}
                       {:event :created
                        :id "b"}]
                      @log)))}])))
               

(deftest create-and-destroy-entity-type-test
  (let [state (atom {})
        root-state (th/atom false)
        entity-types {:my-entity (MyEntityType. state)}
        root-fn (fn []
                  [:my-entity {:test-id "a"}
                   (when @root-state
                     [:my-entity {:test-id "b"}])])]
    (fixture/async-run! [{:when (fn []
                                  (th/render root-fn @canvas {:entity-types entity-types}))
                          :then (fn []
                                  (is (= #{"a"} (set (keys @state)))))}
                         {:when (fn []
                                  (reset! root-state true))
                          :then (fn []
                                  (is (= #{"a" "b"} (set (keys @state)))))}
                         {:when (fn []
                                  (reset! root-state false))
                          :then (fn []
                                  (is (= #{"a"} (set (keys @state)))))}])))

(deftest hot-reload-entity-type-test
  (let [state (atom {})
        entity-types {:my-entity (MyEntityType. state)}
        root-fn-1 (fn []
                    [:my-entity {:test-id "a"}])
        root-fn-2 (fn []
                    [:my-entity {:test-id "b"}])]
    (fixture/async-run! [{:when (fn []
                                  (th/render root-fn-1 @canvas {:entity-types entity-types}))
                          :then (fn []
                                  (is (= #{"a"} (set (keys @state)))))}
                         {:when (fn []
                                  (th/render root-fn-2 @canvas {:entity-types entity-types}))
                          :then (fn []
                                  (is (= #{"b"} (set (keys @state)))))}])))

(deftest updateable-entity-test
  (let [state (atom nil)
        scene-state (th/atom "a")
        the-object (atom nil)
        entity-types {:my-entity (MyUpdateableEntityType. state)}
        root-fn (fn []
                  [:my-entity {:test-id @scene-state}])]
    (fixture/async-run! [{:when (fn []
                                  (th/render root-fn @canvas {:entity-types entity-types}))
                          :then (fn []
                                  (let [object ^js @state]
                                    (is (= "a" (.-testId object)))
                                    (reset! the-object object)))}
                         {:when (fn []
                                  (reset! scene-state "b"))
                          :then (fn []
                                  (let [object ^js @state]
                                    (is (= "b" (.-testId object)))
                                    ;; Ensure the object was not recreated
                                    (is (= @the-object object))))}])))
