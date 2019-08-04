(ns threeagent.component-test
  (:require [cljs.test :refer-macros [deftest is testing]]
            [threeagent.impl.component :as comp])
  (:require-macros [threeagent.macros :refer [defcomponent]]))

(defcomponent :custom-key [c] :render-result)

(deftest defcomponent-test
  (testing "built-in component"
    (is (some? (comp/render-component :object {}))))

  (testing "custom component"
    (is (= :render-result (comp/render-component :custom-key {})))))


