(ns threeagent.child-node-removed-test
  (:require [cljs.test :refer-macros [deftest is testing]]
            [threeagent.impl.virtual-scene :as vscene]
            [threeagent.core :as th]))

(defn root [state]
  ^{:key "root"}
  [:object
   (when @state
     ^{:key "child"}
     [:object])])
  
(deftest root-most-component-re-rendered-test
  (let [test-state (th/atom false)
        scene (vscene/create (partial root test-state))
        changelog (array)
        before (vscene/get-in-scene scene [0])]
    (is (= 1 (.-size (.-children before))))
    (reset! test-state true)
    (vscene/render! scene changelog)
    (let [after (vscene/get-in-scene scene [0 "root" "child"])]
      (is (some? after)))
    (reset! test-state false)
    (vscene/render! scene changelog)
    (let [after (vscene/get-in-scene scene [0 "root" "child"])]
      (is (nil? after)))))
