(ns threeagent.virtual-scene-test
  (:require [cljs.test :refer-macros [deftest is testing]]
            [threeagent.impl.virtual-scene :as vscene]))

(defn test-rootfn []
  [:object {}])

(deftest basic-scene-test
  (let [scene (vscene/create test-rootfn)]
    (is (some? scene))))
