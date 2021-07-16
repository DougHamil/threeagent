(ns threeagent.edge-case-test
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

