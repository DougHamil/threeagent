(ns threeagent.virtual-scene.context-test
  (:require [cljs.test :refer-macros [deftest is testing]]
            [threeagent.virtual-scene.util :refer [find-node-by-id]]
            [threeagent.impl.virtual-scene :as vscene]
            [threeagent.core :as th]))

(deftest context-test
  (let [root-fn (fn []
                  [:object {:id "a"}
                   [{:a :a}
                    [:object {:id "d"}]]
                   [{:test "test"}
                    [:object {:id "b"}
                     [{:test2 "test2"}
                      [:object {:id "c"}]]]]])
        scene (vscene/create root-fn)
        get-ctx #(.-context (find-node-by-id scene %))]
    (is (= {} (get-ctx "a")))
    (is (= {:test "test"} (get-ctx "b")))
    (is (= {:a :a} (get-ctx "d")))
    (is (= {:test "test"
            :test2 "test2"} (get-ctx "c")))))

(deftest context-persists-on-rerender-test
  (testing "parent context is passed to new child nodes"
    (let [state (th/atom false)
          child-fn (fn []
                     [:object
                      (when @state
                        [:object {:id "b"}])])
          root-fn (fn []
                    [:object {:id "a"}
                     [{:test "test"}
                      [child-fn]]])
          scene (vscene/create root-fn)
          get-ctx #(.-context (find-node-by-id scene %))
          changelog (array)]
      (is (= {} (get-ctx "a")))
      (is (nil? (find-node-by-id scene "b")))
      (reset! state true)
      (vscene/render! scene changelog)
      (is (= {:test "test"} (get-ctx "b"))))))


(deftest context-forces-replacement-test
  (let [state (th/atom :a)
        child-2-fn (fn []
                     [:object {:id "c"}])
        child-fn (fn []
                   [:object {:id "b"}
                    [{:test2 "test2"}
                     [child-2-fn]]])
        root-fn (fn []
                  [:object {:id "a"}
                   [{:test @state}
                    [child-fn]]])
        scene (vscene/create root-fn)
        get-ctx #(.-context (find-node-by-id scene %))
        changelog (array)]
    (is (= {} (get-ctx "a")))
    (is (= {:test :a} (get-ctx "b")))
    (is (= {:test :a
            :test2 "test2"} (get-ctx "c")))
    (reset! state :b)
    (vscene/render! scene changelog)
    (is (= {:test :b} (get-ctx "b")))
    (is (= {:test :b
            :test2 "test2"} (get-ctx "c")))))
