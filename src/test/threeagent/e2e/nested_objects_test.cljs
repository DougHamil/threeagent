(ns threeagent.e2e.nested-objects-test
  (:require [cljs.test :refer-macros [deftest is async]]
            [threeagent.threejs.util :refer [create-canvas get-in-scene child-count]]
            [threeagent.core :as th]))

(def state (th/atom {:test-value 1}))

(defn sub3 [v]
   [:object {:test v}])

(defn sub2 []
  [:box {:position [0 0 0]
         :test @(th/cursor state [:test-value])}
   [:object {:test "first-child"}
    [sub3 "embedded-child"]]
   [:object {:test "second-child"}
    [:object {:test "third child"}]]])

(defn root []
  [:object
   [sub2]])

(deftest nested-objects-test
  (async done
         (let [canvas (create-canvas "nested-objects-test")
               ctx (th/render root canvas)
               scene (:threejs-scene ctx)
               before (get-in-scene scene [0 0 0 0])]
           (is (= 1 (child-count scene)))
           (is (= 2 (child-count before)))
           (swap! state assoc :test-value 2)
           ;; Wait for re-render
           (js/setTimeout (fn []
                            (let [after (get-in-scene scene [0 0 0 0])]
                              (is (= 2 (child-count after))))
                            (done))
                          500))))

