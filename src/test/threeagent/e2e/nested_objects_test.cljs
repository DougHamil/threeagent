(ns threeagent.e2e.nested-objects-test
  (:require [cljs.test :refer-macros [deftest is async use-fixtures]]
            [threeagent.e2e.fixture :as fixture]
            [threeagent.threejs.util :refer [get-in-scene child-count]]
            [threeagent.core :as th]))

(defonce canvas (atom nil))

(use-fixtures :each (fixture/with-canvas canvas))

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
  (let [ctx (th/render root @canvas)
        scene (:threejs-scene ctx)
        before (get-in-scene scene [0 0 0 0])]
    (async done
           (fixture/async-run! done
                               [{:when #()
                                 :then (fn []
                                         (is (= 1 (child-count scene)))
                                         (is (= 2 (child-count before))))}
                                {:when (fn []
                                         (swap! state assoc :test-value 2))
                                 :then (fn []
                                         (let [after (get-in-scene scene [0 0 0 0])]
                                           (is (= 2 (child-count after)))))}]))))

