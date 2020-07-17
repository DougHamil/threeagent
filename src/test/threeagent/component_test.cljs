(ns threeagent.component-test
  (:require [cljs.test :refer-macros [deftest is testing]]
            [threeagent.impl.component :as comp]
            ["three" :as three])
  (:require-macros [threeagent.macros :refer [defcomponent]]))

(defcomponent :custom-key [c] :render-result)

(deftest defcomponent-test
  (testing "built-in component"
    (is (some? (comp/render-component :object {}))))

  (testing "custom component"
    (is (= :render-result (comp/render-component :custom-key {})))))

(deftest material-instance-test
  (testing "a provided material instance should be used instead of generating one"
    (let [my-material (new three/MeshPhysicalMaterial
                           (clj->js {:color "red"
                                     :transparency 0.5
                                     :transparent true}))
          obj (comp/render-component :box {:material my-material})]
      (is (not= my-material (.-material obj))))))
