(ns threeagent.virtual-scene.render-dedup-test
  "A node that is queued for render by its own reaction, and is also
   re-rendered by its parent in the same flush (because its args changed),
   should only be rendered once per flush."
  (:require [cljs.test :refer-macros [deftest is testing]]
            [threeagent.impl.virtual-scene :as vscene]
            [threeagent.core :as th]))

(def child-render-count (atom 0))

(defn counting-child [state x]
  (swap! child-render-count inc)
  [:box {:position [@(th/cursor state [:x]) 0 0]}])

(defn root [state]
  [:object
   [counting-child state @(th/cursor state [:x])]])

(deftest no-double-render-test
  (testing "a dirty child re-rendered by its parent is not rendered again from the queue"
    (reset! child-render-count 0)
    (let [test-state (th/atom {:x 0})
          scene (vscene/create (partial root test-state))]
      (is (= 1 @child-render-count))
      (swap! test-state assoc :x 1)
      (vscene/render! scene (array))
      (is (= 2 @child-render-count)
          "child should render exactly once per flush")
      (swap! test-state assoc :x 2)
      (vscene/render! scene (array))
      (is (= 3 @child-render-count)
          "child should render exactly once per flush"))))
