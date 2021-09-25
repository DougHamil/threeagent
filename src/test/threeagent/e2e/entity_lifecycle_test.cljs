(ns threeagent.e2e.entity-lifecycle-test
  (:require [cljs.test :refer-macros [deftest is use-fixtures]]
            ["three" :as three]
            [threeagent.e2e.fixture :as fixture]
            [threeagent.entity :refer [IEntityType]]
            [threeagent.core :as th]))

(defonce canvas (atom nil))

(use-fixtures :each (fixture/with-canvas canvas))

(deftype MyEntityType [state]
  IEntityType
  (create [_ {:keys [test-id]}]
    (let [obj (three/Object3D.)]
      (set! (.-testId obj) test-id)
      (swap! state assoc test-id obj)
      obj))
  (destroy! [_this ^three/Object3D obj]
    (swap! state dissoc (.-testId obj))))

(deftest entity-type-test
  (let [state (atom {})
        entity-types {:my-entity (MyEntityType. state)}
        root-fn (fn []
                  [:my-entity {:test-id "a"}
                   [:my-entity {:test-id "b"}]])]
    (th/render root-fn @canvas
               {:entity-types entity-types})
    (is (= #{"a" "b"} (set (keys @state))))
    (is (= "a" (.-testId ^js (get @state "a"))))
    (is (= "b" (.-testId ^js (get @state "b"))))))
                  
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
