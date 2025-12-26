(ns threeagent.e2e.threejs-geometries-test
  (:require [cljs.test :refer-macros [deftest use-fixtures]]
            [threeagent.e2e.fixture :as fixture]
            [threeagent.core :as th]))

(defonce canvas (atom nil))

(use-fixtures :each (fixture/with-canvas canvas))

(deftest entity-type-test
  (let [root-fn (fn []
                  [:object
                   [:box {}]
                   [:sphere {}]
                   [:plane {}]
                   [:octahedron {}]
                   [:cylinder {}]
                   [:circle {}]
                   [:cone {}]
                   [:dodecahedron {}]
                   [:icosahedron {}]
                   [:ring {}]
                   [:torus {}]
                   [:torus-knot {}]])]
    ;; Just assert no error is thrown during rendering
    (th/render root-fn @canvas)))
