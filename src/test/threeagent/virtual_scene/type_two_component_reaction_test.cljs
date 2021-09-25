(ns threeagent.virtual-scene.type-two-component-reaction-test
  (:require [cljs.test :refer-macros [deftest is testing]]
            [threeagent.impl.virtual-scene :as vscene]
            [threeagent.core :as th]))

(def outer-render-count (atom 0))
(def inner-render-count (atom 0))

(defn type-2-component [state]
  (swap! outer-render-count inc)
  (let [outer-val @(th/cursor state [:outer-value])]
    (fn [_]
      (swap! inner-render-count inc)
      (let [inner-val @(th/cursor state [:inner-value])]
        [:object]))))

(defn root [state]
  [:object
   [type-2-component state]])

(deftest type-two-component-reaction-test
  (testing "A subscription to state in the outer function should force re-render of said function"
    (reset! outer-render-count 0)
    (reset! inner-render-count 0)
    (let [test-state (th/atom {:outer-value 0
                               :inner-value 0})
          scene (vscene/create (partial root test-state))
          changelog (array)]
      (is (= 1 @outer-render-count))
      (is (= 1 @inner-render-count))
      (swap! test-state update :outer-value inc)
      (vscene/render! scene changelog)
      (is (= 2 @outer-render-count))
      (is (= 2 @inner-render-count))
      (swap! test-state update :inner-value inc)
      (vscene/render! scene changelog)
      (is (= 2 @outer-render-count))
      (is (= 3 @inner-render-count)))))
      ;(vscene/print-tree (.-root scene)))))
