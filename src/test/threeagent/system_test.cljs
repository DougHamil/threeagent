(ns threeagent.system-test
  (:require [cljs.test :refer-macros [deftest is testing async]]
            [threeagent.system :refer [ISystem]]
            [threeagent.threejs.util :refer [create-canvas]]
            [threeagent.core :as th]))


(defrecord MySystem [sys-state]
  ISystem
  (init [_ _ctx])
  (destroy [_ _ctx])
  (on-entity-added [_ key _obj config]
    (swap! sys-state conj config))
  (on-entity-removed [_ key _obj config]
    (swap! sys-state disj config))
  (tick [_ _]))

(defn root [state]
  [:object
    [:object {:custom-system {:value "a"}}
      [:object {:custom-system {:value "b"}}]
      (when (:add-third? @state)
        [:object {:custom-system {:value "c"}}])]])

(deftest persistent-custom-system-test
  (let [state (th/atom {})
        sys-state (atom #{})
        my-system (->MySystem sys-state)]
    (th/render (partial root state) (create-canvas "render-system-test-1")
               {:systems {:custom-system my-system}})
    (is (= 2 (count @sys-state)))
  ;; Ensure consistency across re-render
    (th/render (partial root state) (create-canvas "render-system-test-1")
               {:systems {:custom-system my-system}})
    (is (= 2 (count @sys-state)))))

(deftest renewed-system-test
  (let [state (th/atom {})
        sys-state (atom #{})
        my-system (->MySystem sys-state)]
    (th/render (partial root state) (create-canvas "render-system-test-2")
                {:systems {:custom-system my-system}})
    (is (= 2 (count @sys-state)))
    (is (contains? @sys-state {:value "b"}))
    (is (contains? @sys-state {:value "a"}))
    ;; Ensure consistency across re-render
    (th/render (partial root state) (create-canvas "render-system-test-2")
                {:systems {:custom-system my-system}})
    (is (= 2 (count @sys-state)))))

(deftest reactive-render-system-test
  (async done
         (let [state (th/atom {})
               sys-state (atom #{})
               my-system (->MySystem sys-state)]
           (th/render (partial root state) (create-canvas "render-system-test-3")
                      {:systems {:custom-system my-system}})
           (is (= 2 (count @sys-state)))
           (swap! state assoc :add-third? true)
           ;; Wait for re-render
           (js/setTimeout (fn []
                            (is (= 3 (count @sys-state)))
                            (is (contains? @sys-state {:value "c"}))
                            (done))
                          500))))
