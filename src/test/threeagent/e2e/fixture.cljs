(ns threeagent.e2e.fixture
  (:require [threeagent.threejs.util :refer [create-canvas
                                             destroy-canvas!]]
            [clojure.test :refer [async]]))

(defn with-canvas [state]
  {:before (fn []
             (reset! state (create-canvas "e2e-fixture")))
   :after (fn []
            (destroy-canvas! "e2e-fixture")
            (reset! state nil))})

(defn async-run!
  ([steps]
   (async done
          (async-run! done steps)))
  ([done steps]
   (if (seq steps)
     (let [{:keys [when then]} (first steps)]
       (when)
       (js/requestAnimationFrame (fn []
                                   (then)
                                   (async-run! done (rest steps)))))
     (done))))
