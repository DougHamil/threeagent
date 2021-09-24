(ns threeagent.virtual-scene-test
  (:require [cljs.test :refer-macros [deftest is testing]]
            [threeagent.impl.virtual-scene :as vscene]
            [threeagent.core :as th]))


(defn test-rootfn []
  [:object])

(deftest basic-scene-test
  (let [scene (vscene/create test-rootfn)]
    (testing "scene is created"
      (is (some? scene)))))

(defn- test-sub-comp [k]
  [k])

(defn- test-sub-comp2 [k]
  [k])

(defn fn-to-literal-replace-root [state]
  [:object
    (when @(th/cursor state [:value])
      [:test-result])
    [test-sub-comp :fn-result]])

(defn fn-to-same-fn-replace-root [state]
  [:object
    (when @(th/cursor state [:value])
      [test-sub-comp2 :fn-result-after])
    [test-sub-comp2 :fn-result-before]])

(defn- test-comp-1 []
  [:fn-result-before])

(defn- test-comp-2 []
  [:fn-result-after])

(defn fn-to-diff-fn-replace-root [state]
  [:object
   (when @(th/cursor state [:value])
     [test-comp-2])
   [test-comp-1]])

(deftest replace-fns-test
  (testing "Replacing a fn child form with literal"
    (let [test-state (th/atom {:value nil})
          scene (vscene/create (partial fn-to-literal-replace-root test-state))
          changelog (array)
          before (.-data (vscene/get-in-scene scene [0 0 0 0]))]
        (swap! test-state assoc :value true)
        (vscene/render! scene changelog)
        (let [after (.-data (vscene/get-in-scene scene [0 0 0]))]
          (is (= :fn-result (:component-key before)))
          (is (= :test-result (:component-key after))))))
  (testing "Replacing a fn call with same fn"
    (let [test-state (th/atom {:value nil})
          scene (vscene/create (partial fn-to-same-fn-replace-root test-state))
          changelog (array)
          before (.-data (vscene/get-in-scene scene [0 0 0 0]))]
      (swap! test-state assoc :value true)
      ;(vscene/print-tree (.-root scene))
      (vscene/render! scene changelog)
      ;(vscene/print-tree (.-root scene))
      (let [after (.-data (vscene/get-in-scene scene [0 0 0 0]))]
          (is (= :fn-result-before (:component-key before)))
          (is (= :fn-result-after (:component-key after))))
      (swap! test-state assoc :value false)
      (vscene/render! scene changelog)
      ;(vscene/print-tree (.-root scene))
      (is (= :fn-result-before  (:component-key (.-data (vscene/get-in-scene scene [0 0 0 0])))))))
  (testing "Replacing a fn call with a different fn"
    (let [test-state (th/atom {:value nil})
          scene (vscene/create (partial fn-to-diff-fn-replace-root test-state))
          changelog (array)
          before (.-data (vscene/get-in-scene scene [0 0 0 0]))]
      (swap! test-state assoc :value true)
      (vscene/render! scene changelog)
      (let [after (.-data (vscene/get-in-scene scene [0 0 0 0]))]
          (is (= :fn-result-before (:component-key before)))
          (is (= :fn-result-after (:component-key after)))))))

