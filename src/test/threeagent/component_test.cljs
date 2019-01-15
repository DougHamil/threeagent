(ns threeagent.component-test
  (:require [cljs.test :refer-macros [deftest is testing]]
            [threeagent.impl.component :as comp]))

(deftest register-component-test
  (testing "built-in renderer"
    (is (some? (comp/render-component :object {}))))
  (testing "custom renderer"
    (comp/defrenderer :custom-key (fn [c] :render-result))
    (is (= :render-result (comp/render-component :custom-key {})))))


