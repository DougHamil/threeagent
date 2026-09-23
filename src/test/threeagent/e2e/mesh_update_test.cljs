(ns threeagent.e2e.mesh-update-test
  (:require [cljs.test :refer-macros [deftest is use-fixtures]]
            [threeagent.e2e.fixture :as fixture]
            [threeagent.core :as th]
            [threeagent.tsl :refer-macros [defshader]]))

(defonce canvas (atom nil))

(use-fixtures :each (fixture/with-canvas canvas))

(defshader tinted [{:keys [strength]}]
  (* [1 0.5 0.25] strength))

(deftest geometry-survives-non-geometry-updates
  (let [state (th/atom {:width 1 :strength 1})
        mesh (atom nil)
        geometries (atom [])
        record! #(swap! geometries conj (.-geometry ^js @mesh))
        root-fn (fn []
                  (let [{:keys [width strength]} @state]
                    [:box {:width width
                           :on-added #(reset! mesh %)
                           :material {:shader tinted
                                      :uniforms {:strength strength}}}]))]
    (fixture/async-run!
     [{:when #(th/render root-fn @canvas)
       :then record!}
      {:when #(swap! state assoc :strength 3)
       :then (fn []
               (record!)
               (is (identical? (first @geometries) (second @geometries))
                   "a uniform change keeps the geometry (and its GPU buffers)"))}
      {:when #(swap! state assoc :width 2)
       :then (fn []
               (record!)
               (is (not (identical? (second @geometries) (nth @geometries 2)))
                   "a geometry change still rebuilds it"))}])))
