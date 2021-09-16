(ns threeagent.system-test
  (:require [cljs.test :refer-macros [deftest is testing async]]
            [threeagent.system :refer [ISystem]]
            [threeagent.threejs.util :refer [create-canvas]]
            [threeagent.core :as th]))


(defrecord MySystem [sys-state]
  ISystem
  (init [_ _ctx])
  (destroy [_ _ctx])
  (on-entity-added [_ id _obj _config]
    (swap! sys-state conj id))
  (on-entity-removed [_ id _obj _config]
    (swap! sys-state disj id))
  (tick [_ _]))

(defn root [state]
  [:object
   [:object {:id "a"
             :custom-system {}}
    [:object {:id "b"
              :custom-system {}}
     (when (:add-third? @state)
       ^{:key "third"}
       [:object {:id "c"
                 :custom-system {}}])]]])

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
    (is (contains? @sys-state "b"))
    (is (contains? @sys-state "a"))
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
                            (is (contains? @sys-state "c"))
                            (swap! state assoc :add-third? false)
                            (js/setTimeout (fn []
                                             (is (= 2 (count @sys-state)))
                                             (is (not (contains? @sys-state "c")))
                                             (done))
                                           500))
                          500))))
