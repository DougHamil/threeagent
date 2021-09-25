(ns threeagent.virtual-scene.edge-case-test
  (:require [cljs.test :refer-macros [deftest is testing]]
            [threeagent.impl.virtual-scene :as vscene]
            [threeagent.core :as th]))

(defn subcomponent [state]
  [:box])

(defn fn-component [state idx]
  (let [state (th/cursor state [idx])]
    (if @state
      [subcomponent state]
      [:object])))

(defn root [state]
  [:object
   [fn-component state 0]])

(deftest update-from-literal-to-fn-component-test
  (testing "a component should be able to toggle between returning a literal and another component fn"
    (let [test-state (th/atom [])
          scene (vscene/create (partial root test-state))
          changelog (array)
          before (.-data (vscene/get-in-scene scene [0 0 0 0]))]
      (swap! test-state conj {:value true})
      (vscene/render! scene changelog)
      (is (vscene/get-in-scene scene [0 0 0]))
      (let [after (.-data (vscene/get-in-scene scene [0 0 0 0 0 0]))]
        (is (= :object (:component-key before)))
        (is (= :box (:component-key after))))
      (reset! test-state [])
      (vscene/render! scene changelog)
      (let [after (.-data (vscene/get-in-scene scene [0 0]))]
        (is (= :object (:component-key before)))
        (is (= :object (:component-key after)))))))

(defn sub3 [v]
   [:object {:test v}])

(defn sub2 [state]
  [:object {:position [0 0 0]
            :test @(th/cursor state [:test-value])}
   [:object {:test "first-child"}
    [sub3 "test"]]
   [:object {:test "second-child"}
    [:object {:test "third child"}]]])

(defn root2 [state]
  [:object
   [sub2 state]])


(deftest reactive-rerender-parent-with-two-subobjects
  (let [test-state (th/atom {:test-value 1})
        scene (vscene/create (partial root2 test-state))
        changelog (array)
        before (vscene/get-in-scene scene [0 0 0 0])]
    (is (= 2 (.-size (.-children before))))
    (swap! test-state assoc :test-value 2)
    (vscene/render! scene changelog)
    (let [after (vscene/get-in-scene scene [0 0 0 0])]
      (is (= 2 (.-size (.-children after)))))))
