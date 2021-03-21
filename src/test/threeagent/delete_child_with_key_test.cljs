(ns threeagent.delete-child-with-key-test
  (:require [cljs.test :refer-macros [deftest is testing]]
            [threeagent.impl.virtual-scene :as vscene]
            [threeagent.core :as th]))

(defn child-component [state entity-key]
  [:box])

(defn parent-component [state]
  ^{:key "PARENT_INNER"}
  [:object
   (for [child (:children @state)]
     ^{:key child}
     [child-component state child])])

(defn root [state]
  ^{:key "ROOT"}
  [:object
    ^{:key "PARENT"}
    [parent-component state :entity]])

(deftest delete-child-with-key-test
  (testing "Deleting a child key should safely remove that child from the parent"
    (let [test-state (th/atom {:children #{"a" "b"}})
          scene (vscene/create (partial root test-state))
          changelog (array)]
      (is (= 2 (.-size (.-children (vscene/get-in-scene scene [0 "ROOT" "PARENT" "PARENT_INNER" 0])))))
      (swap! test-state update-in [:children] disj "a")
      (vscene/render! scene changelog)
      (is (= 1 (.-size (.-children (vscene/get-in-scene scene [0 "ROOT" "PARENT" "PARENT_INNER" 0]))))))))
