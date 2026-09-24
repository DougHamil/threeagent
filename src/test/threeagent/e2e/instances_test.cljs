(ns threeagent.e2e.instances-test
  (:require [cljs.test :refer-macros [deftest is use-fixtures]]
            [threeagent.e2e.fixture :as fixture]
            [threeagent.core :as th]))

(defonce canvas (atom nil))

(use-fixtures :each (fixture/with-canvas canvas))

(deftest mesh-instances
  (let [state (th/atom {:instances 100})
        objs (atom {})
        root-fn (fn []
                  (let [{:keys [instances culled]} @state]
                    [:object
                     [:box (cond-> {:on-added #(swap! objs assoc :box %)}
                             instances (assoc :instances instances)
                             (some? culled) (assoc :frustum-culled culled))]
                     [:object {:frustum-culled false
                               :on-added #(swap! objs assoc :group %)}]]))
        box (fn [] ^js (:box @objs))]
    (fixture/async-run!
     [{:when #(th/render root-fn @canvas)
       :then (fn []
               (is (= 100 (.-count (box))))
               (is (false? (.-frustumCulled (box))) "instanced meshes aren't culled")
               (is (false? (.-frustumCulled ^js (:group @objs))) ":frustum-culled works on any entity"))}
      {:when #(swap! state assoc :instances 5)
       :then (fn [] (is (= 5 (.-count (box)))))}
      {:when #(swap! state assoc :culled true)
       :then (fn [] (is (true? (.-frustumCulled (box))) "an explicit :frustum-culled wins"))}
      {:when #(swap! state assoc :instances nil :culled nil)
       :then (fn []
               (is (= 1 (.-count (box))))
               (is (true? (.-frustumCulled (box)))))}])))
