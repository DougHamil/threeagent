(ns threeagent.component-test
  (:require [cljs.test :refer-macros [deftest is testing]]
            [threeagent.impl.component :as comp])
  (:require-macros [threeagent.impl.component-macros :refer [defrenderer]]))

(defrenderer :custom-key [c] :render-result)

(deftest register-component-test
  (testing "built-in renderer"
    (is (some? (comp/render-component :object {}))))

  (testing "custom renderer"
    (is (= :render-result (comp/render-component :custom-key {})))))


