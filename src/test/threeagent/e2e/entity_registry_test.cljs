(ns threeagent.e2e.entity-registry-test
  (:require [cljs.test :refer-macros [deftest is use-fixtures]]
            [threeagent.e2e.fixture :as fixture]
            [threeagent.core :as th]))

(defonce canvas (atom nil))

(use-fixtures :each (fixture/with-canvas canvas))

(deftest basic-entity-registry-test
  (let [registry (atom {})
        root-fn (fn []
                  [:object
                   [:box {:id :my-box}]
                   [:sphere {:id :my-sphere}]])]
    (th/render root-fn @canvas {:entity-registry registry})
    (is (= #{:my-box :my-sphere} (set (keys @registry))))
    (is (some? (get @registry :my-box)))
    (is (some? (get @registry :my-sphere)))))

(deftest entity-registry-reactive-test
  (let [registry (atom {})
        state (th/atom true)
        root-fn (fn []
                  [:object
                   [:box {:id :my-box}]
                   (when @state
                     [:sphere {:id :my-sphere}])])]
    (fixture/async-run!
     [{:when (fn []
               (th/render root-fn @canvas {:entity-registry registry}))
       :then (fn []
               (is (= #{:my-box :my-sphere} (set (keys @registry)))))}
      {:when (fn []
               (reset! state false))
       :then (fn []
               (is (= #{:my-box} (set (keys @registry))))
               (is (nil? (get @registry :my-sphere))))}
      {:when (fn []
               (reset! state true))
       :then (fn []
               (is (= #{:my-box :my-sphere} (set (keys @registry)))))}])))

(deftest entity-registry-nested-context-test
  (let [registry (atom {})
        root-fn (fn []
                  [:object {:id :root}
                   [{:some "context"}
                    [:box {:id :child}]]])]
    (th/render root-fn @canvas {:entity-registry registry})
    (is (= #{:root :child} (set (keys @registry))))
    (is (some? (get @registry :root)))
    (is (some? (get @registry :child)))))

(deftest entity-registry-without-id-test
  (let [registry (atom {})
        root-fn (fn []
                  [:object
                   [:box]
                   [:sphere {:id :my-sphere}]])]
    (th/render root-fn @canvas {:entity-registry registry})
    (is (= #{:my-sphere} (set (keys @registry))))
    (is (= 1 (count @registry)))))

(deftest entity-registry-hot-reload-test
  (let [registry (atom {})
        root-fn-1 (fn []
                    [:object
                     [:box {:id :box-1}]
                     [:sphere {:id :sphere-1}]])
        root-fn-2 (fn []
                    [:object
                     [:box {:id :box-2}]
                     [:sphere {:id :sphere-2}]])]
    (fixture/async-run!
     [{:when (fn []
               (th/render root-fn-1 @canvas {:entity-registry registry}))
       :then (fn []
               (is (= #{:box-1 :sphere-1} (set (keys @registry)))))}
      {:when (fn []
               (th/render root-fn-2 @canvas {:entity-registry registry}))
       :then (fn []
               (is (= #{:box-2 :sphere-2} (set (keys @registry))))
               (is (nil? (get @registry :box-1)))
               (is (nil? (get @registry :sphere-1))))}])))

(deftest entity-registry-reuses-same-id-test
  (let [registry (atom {})
        state (th/atom :box)
        root-fn (fn []
                  [:object
                   (case @state
                     :box [:box {:id :my-entity}]
                     :sphere [:sphere {:id :my-entity}])])]
    (fixture/async-run!
     [{:when (fn []
               (th/render root-fn @canvas {:entity-registry registry}))
       :then (fn []
               (is (= #{:my-entity} (set (keys @registry))))
               (is (= "Mesh" (.-type ^js (get @registry :my-entity)))))}
      {:when (fn []
               (reset! state :sphere))
       :then (fn []
               (is (= #{:my-entity} (set (keys @registry))))
               ;; Should now be a sphere mesh instead of box mesh
               (is (= "Mesh" (.-type ^js (get @registry :my-entity)))))}])))
