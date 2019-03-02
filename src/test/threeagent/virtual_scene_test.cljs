(ns threeagent.virtual-scene-test
  (:require [cljs.test :refer-macros [deftest is testing]]
            [threeagent.impl.virtual-scene :as vscene]
            [threeagent.alpha.core :as th]))


(defn test-rootfn []
  [:object {}])

(deftest basic-scene-test
  (let [scene (vscene/create test-rootfn)]
    (testing "scene is created"
      (is (some? scene)))))

(def test-state (th/atom {:value nil}))

(defn- test-sub-comp []
  [:fn-result])

(defn nil-value-rootfn []
  [:object
    (when-let [t @(th/cursor test-state [:value])]
      [:test-result])
    [test-sub-comp]])

(deftest nil-value-in-form-test
  (let [scene (vscene/create nil-value-rootfn)
        changelog (array)]
    (testing "Nil value in form is skipped"
      (vscene/print-tree (.-root scene))
      (swap! test-state assoc :value true)
      (vscene/render! scene changelog)
      (vscene/print-tree (.-root scene))
      (.log js/console changelog)
      (doseq [c changelog]
        (println c))
      (is (some? scene)))))
