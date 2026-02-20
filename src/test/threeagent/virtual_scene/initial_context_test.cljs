(ns threeagent.virtual-scene.initial-context-test
  (:require [cljs.test :refer-macros [deftest is]]
            [threeagent.virtual-scene.util :refer [find-node-by-id]]
            [threeagent.impl.virtual-scene :as vscene]))

(deftest initial-context-propagation-test
  (let [root-fn (fn []
                  [:object {:id "a"}
                   [:object {:id "b"}]])
        scene (vscene/create root-fn {:threeagent/scene-key :world})
        get-ctx #(.-context (find-node-by-id scene %))]
    ;; Initial context propagates to all descendant nodes
    (is (= :world (:threeagent/scene-key (get-ctx "a"))))
    (is (= :world (:threeagent/scene-key (get-ctx "b"))))))

(deftest initial-context-merges-with-inline-context-test
  (let [root-fn (fn []
                  [:object {:id "a"}
                   [{:extra "data"}
                    [:object {:id "b"}]]])
        scene (vscene/create root-fn {:threeagent/scene-key :ui})
        get-ctx #(.-context (find-node-by-id scene %))]
    ;; Initial context is present at root
    (is (= :ui (:threeagent/scene-key (get-ctx "a"))))
    ;; Inline context merges with initial context
    (is (= {:threeagent/scene-key :ui
            :extra "data"} (get-ctx "b")))))

(deftest initial-context-defaults-to-empty-test
  ;; Backwards compatible: single-arity create passes empty context
  (let [root-fn (fn []
                  [:object {:id "a"}])
        scene (vscene/create root-fn)
        get-ctx #(.-context (find-node-by-id scene %))]
    (is (= {} (get-ctx "a")))))
