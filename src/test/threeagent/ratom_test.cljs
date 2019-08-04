(ns threeagent.ratom-test
  (:require [threeagent.core :as th]
            [cljs.test :refer-macros [deftest is testing]]))

(deftest ratom-test
  (testing "ratom is creatable"
    (is (= 1 (:test @(th/atom {:test 1}))))))


