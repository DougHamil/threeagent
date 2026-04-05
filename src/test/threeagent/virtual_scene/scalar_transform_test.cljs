(ns threeagent.virtual-scene.scalar-transform-test
  (:require [cljs.test :refer-macros [deftest is testing]]
            [threeagent.impl.virtual-scene :as vscene]
            [threeagent.core :as th]
            [threeagent.virtual-scene.util :refer [find-node-by-id]]))

(defn- scalar-root [state]
  (let [{:keys [position scale rotation]} @state]
    [:object {:id :test-node
              :position position
              :scale scale
              :rotation rotation}]))

(deftest scalar-transform-test
  (testing "Scalar values are expanded to [v v v] vectors"
    (let [state (th/atom {:position 5 :scale 2 :rotation 0.5})
          scene (vscene/create (partial scalar-root state))
          changelog (array)]
      (vscene/render! scene changelog)
      (let [node (find-node-by-id scene :test-node)
            data (.-data node)]
        (is (= [5 5 5] (:position data)))
        (is (= [2 2 2] (:scale data)))
        (is (= [0.5 0.5 0.5] (:rotation data))))))

  (testing "Vector values still work unchanged"
    (let [state (th/atom {:position [1 2 3] :scale [4 5 6] :rotation [0.1 0.2 0.3]})
          scene (vscene/create (partial scalar-root state))
          changelog (array)]
      (vscene/render! scene changelog)
      (let [node (find-node-by-id scene :test-node)
            data (.-data node)]
        (is (= [1 2 3] (:position data)))
        (is (= [4 5 6] (:scale data)))
        (is (= [0.1 0.2 0.3] (:rotation data))))))

  (testing "Omitted values get correct defaults"
    (let [state (th/atom {})
          scene (vscene/create (partial scalar-root state))
          changelog (array)]
      (vscene/render! scene changelog)
      (let [node (find-node-by-id scene :test-node)
            data (.-data node)]
        (is (= [0 0 0] (:position data)))
        (is (= [1.0 1.0 1.0] (:scale data)))
        (is (= [0 0 0] (:rotation data))))))

  (testing "Updating from vector to scalar"
    (let [state (th/atom {:position [1 2 3] :scale [4 5 6] :rotation [0.1 0.2 0.3]})
          scene (vscene/create (partial scalar-root state))
          changelog (array)]
      (vscene/render! scene changelog)
      (reset! state {:position 10 :scale 3 :rotation 1})
      (vscene/render! scene changelog)
      (let [node (find-node-by-id scene :test-node)
            data (.-data node)]
        (is (= [10 10 10] (:position data)))
        (is (= [3 3 3] (:scale data)))
        (is (= [1 1 1] (:rotation data)))))))
