(ns threeagent.virtual-scene.child-key-stability-test
  "The initial deep build (->node) and the update-time shallow diff
   (->node-shallow) must compute identical child keys, otherwise keyed
   children are destroyed and recreated on the first update after mount."
  (:require [cljs.test :refer-macros [deftest is testing]]
            [threeagent.impl.virtual-scene :as vscene]
            [threeagent.virtual-scene.util :as util]
            [threeagent.core :as th]))

(defn- changelog-actions [changelog]
  (set (map second changelog)))

(defn- first-child [node]
  (first (es6-iterator-seq (.values (.-children node)))))

(defn config-key-root [state]
  [:object {:test @state}
   [:box {:key "a" :id "box-a"}]
   [:box {:key "b" :id "box-b"}]])

(deftest config-map-key-stability-test
  (testing "children keyed via a config-map :key survive a parent re-render"
    (let [test-state (th/atom 0)
          scene (vscene/create (partial config-key-root test-state))
          changelog (array)
          before (util/find-node-by-id scene "box-a")]
      (is (some? before))
      (swap! test-state inc)
      (vscene/render! scene changelog)
      (let [after (util/find-node-by-id scene "box-a")]
        (is (identical? before after)
            "node keyed by config-map :key should be updated in place")
        (is (not (contains? (changelog-actions changelog) :remove)))
        (is (not (contains? (changelog-actions changelog) :add)))))))

(defn portal-key-root [state]
  [:object {:test @state :id "portal-parent"}
   ^{:key "p"} [:> ["target"] {:position [1 2 3]}]])

(deftest portal-meta-key-stability-test
  (testing "portal children keyed via ^:key metadata survive a parent re-render"
    (let [test-state (th/atom 0)
          scene (vscene/create (partial portal-key-root test-state))
          changelog (array)
          before (first-child (util/find-node-by-id scene "portal-parent"))]
      (is (some? before))
      (swap! test-state inc)
      (vscene/render! scene changelog)
      (let [after (first-child (util/find-node-by-id scene "portal-parent"))]
        (is (identical? before after)
            "portal node keyed by ^:key metadata should be updated in place")
        (is (not (contains? (changelog-actions changelog) :remove)))
        (is (not (contains? (changelog-actions changelog) :add)))))))

(defn empty-sibling-root [state]
  [:object {:test @state}
   (for [child []]
     [:box])
   [:box {:id "box-c"}]])

(deftest empty-collection-sibling-stability-test
  (testing "an empty collection child does not shift sibling indices between mount and update"
    (let [test-state (th/atom 0)
          scene (vscene/create (partial empty-sibling-root test-state))
          changelog (array)
          before (util/find-node-by-id scene "box-c")]
      (is (some? before))
      (swap! test-state inc)
      (vscene/render! scene changelog)
      (let [after (util/find-node-by-id scene "box-c")]
        (is (identical? before after)
            "sibling after an empty collection should be updated in place")
        (is (not (contains? (changelog-actions changelog) :remove)))
        (is (not (contains? (changelog-actions changelog) :add)))))))
