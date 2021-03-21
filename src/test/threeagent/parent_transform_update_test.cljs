(ns threeagent.parent-transform-update-test
  (:require [cljs.test :refer-macros [deftest is testing]]
            [threeagent.impl.virtual-scene :as vscene]
            [threeagent.core :as th]))

(def child-render-count (atom 0))

(defn child-component [state entity-key]
  (swap! child-render-count inc)
  (let [rotation @(th/cursor state [entity-key :rotation])]
    [:box {:rotation rotation}]))

(defn parent-component [state entity-key]
  (let [position @(th/cursor state [entity-key :position])]
    ^{:key "THIS-PARENT"}
    [:object {:position position}
      ^{:key "THIS-CHILD"}
     [child-component state entity-key]]))

(defn root [state]
  [:object
   [parent-component state :entity]])

(deftest update-parent-transform-only-test
  (testing "Updating a parent component's transform should not force a re-render of children"
    (reset! child-render-count 0)
    (let [test-state (th/atom {:entity {:position [0 0 0]
                                        :rotation [0 0 0]}})
          scene (vscene/create (partial root test-state))
          changelog (array)]
      (vscene/render! scene changelog)
      ;; Update just the parent component
      (swap! test-state assoc-in [:entity :position] [0 1 0])
      (vscene/render! scene changelog)
      ;; Child should not be re-rendered
      (is (= 1 @child-render-count))
      ;; Update just the child component
      (swap! test-state assoc-in [:entity :rotation] [0 1 0])
      (vscene/render! scene changelog)
      ;; Child should be re-rendered
      (is (= 2 @child-render-count))
      ;; Update both child and parent
      (swap! test-state assoc-in [:entity :position] [0 2 0])
      (swap! test-state assoc-in [:entity :rotation] [0 2 0])
      (vscene/render! scene changelog)
      ;; Child should be re-rendered
      (is (= 3 @child-render-count)))))

