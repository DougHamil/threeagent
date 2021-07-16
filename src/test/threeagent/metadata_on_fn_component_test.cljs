(ns threeagent.metadata-on-fn-component-test
  (:require [cljs.test :refer-macros [deftest is testing]]
            [threeagent.impl.virtual-scene :as vscene]
            [threeagent.core :as th]))

(defn- fn-a [_])
(defn- fn-b [_])


(defn child-component [state entity]
  (fn [_ _]
    ^{:child-inner @state}
    [:object]))

(defn parent-component [state]
  ^{:parent-inner true}
  [:object
    [child-component state]])

(defn root [state]
  [:object
   ^{:parent-outer true}
   [parent-component state]])

(deftest metadata-on-fn-component-test-test
  (testing "fn components should maintain the metadata of the fn-call child component"
    (let [test-state (th/atom "test")
          scene (vscene/create (partial root test-state))
          changelog (array)]
      (is (:parent-outer (.-meta (vscene/get-in-scene scene [0 0 0]))))
      (is (:parent-inner (.-meta (vscene/get-in-scene scene [0 0 0 0]))))
      (is (= "test" (:child-inner (.-meta (vscene/get-in-scene scene [0 0 0 0 0 0])))))
      (reset! test-state "test2")
      (vscene/render! scene changelog)
      (is (= "test2" (:child-inner (.-meta (vscene/get-in-scene scene [0 0 0 0 0 0]))))))))


