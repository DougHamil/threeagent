(ns threeagent.e2e.system-test
  (:require [cljs.test :refer-macros [deftest is use-fixtures]]
            [threeagent.e2e.fixture :as fixture]
            [threeagent.system :refer [ISystem]]
            [threeagent.core :as th]))

(defonce canvas (atom nil))

(use-fixtures :each (fixture/with-canvas canvas))

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

(defn child [system?]
  (if system?
    [:object {:id "a"
              :custom-system {}}]
    [:object {:id "b"}]))

(defn root2 [state]
  [:object
   [child @state]])

(deftest persistent-custom-system-test
  (let [state (th/atom {})
        sys-state (atom #{})
        my-system (->MySystem sys-state)]
    (th/render (partial root state) @canvas
               {:systems {:custom-system my-system}})
    (is (= 2 (count @sys-state)))
 ;; Ensure consistency across re-render
    (th/render (partial root state) @canvas
               {:systems {:custom-system my-system}})
    (is (= 2 (count @sys-state)))))

(deftest renewed-system-test
  (let [state (th/atom {})
        sys-state (atom #{})
        my-system (->MySystem sys-state)]
    (th/render (partial root state) @canvas
               {:systems {:custom-system my-system}})
    (is (= 2 (count @sys-state)))
    (is (contains? @sys-state "b"))
    (is (contains? @sys-state "a"))
  ;; Ensure consistency across re-render
    (th/render (partial root state) @canvas
               {:systems {:custom-system my-system}})
    (is (= 2 (count @sys-state)))))

(deftest reactive-render-system-test
  (let [state (th/atom {})
        sys-state (atom #{})
        my-system (->MySystem sys-state)]
    (fixture/async-run! 
                        [{:when (fn []
                                  (th/render (partial root state) @canvas
                                             {:systems {:custom-system my-system}}))
                          :then (fn []
                                  (is (= 2 (count @sys-state))))}
                         {:when (fn []
                                  (swap! state assoc :add-third? true))
                          :then (fn []
                                  (is (= 3 (count @sys-state)))
                                  (is (contains? @sys-state "c")))}
                         {:when (fn []
                                  (swap! state assoc :add-third? false))
                          :then (fn []
                                  (is (= 2 (count @sys-state)))
                                  (is (not (contains? @sys-state "c"))))}])))

(deftest rerender-child-component-system-lifecycle-test
  (let [state (th/atom true)
        sys-state (atom #{})
        my-system (->MySystem sys-state)]
    (fixture/async-run! 
                        [{:when (fn []
                                  (th/render (partial root2 state) @canvas
                                             {:systems {:custom-system my-system}}))
                          :then (fn []
                                  (is (= #{"a"} @sys-state)))}
                         {:when (fn []
                                  (reset! state false))
                          :then (fn []
                                  (is (= #{} @sys-state)))}])))
