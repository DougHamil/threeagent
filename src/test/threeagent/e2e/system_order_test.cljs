(ns threeagent.e2e.system-order-test
  (:require [cljs.test :refer-macros [deftest is use-fixtures]]
            [threeagent.e2e.fixture :as fixture]
            [threeagent.system :refer [ISystem]]
            [threeagent.core :as th]))

(defonce canvas (atom nil))

(use-fixtures :each (fixture/with-canvas canvas))

(defrecord MySystem [log]
  ISystem
  (init [_ _ctx])
  (destroy [_ _ctx])
  (on-entity-added [_ _ id _obj _config]
    (swap! log conj [:add id])
    #(swap! log conj [:post-add id]))
  (on-entity-removed [_ _ id _obj _config]
    (swap! log conj [:remove id])
    #(swap! log conj [:post-remove id]))
  (tick [_ _]))

(deftest system-ordering-test
  (let [log (atom [])
        state (th/atom true)
        root-fn (fn []
                  [:object
                   (when @state
                     [:object {:id "a"
                               :my-system {}}
                      [:object {:id "b"
                                :my-system {}}]])])
        my-system (->MySystem log)]
    (fixture/async-run!
     [{:when (fn []
               (th/render root-fn @canvas
                          {:systems {:my-system my-system}}))
       :then (fn []
               (is (= [[:add "a"]
                       [:add "b"]
                       [:post-add "b"]
                       [:post-add "a"]]
                      @log)))}
      {:when (fn []
               (reset! log [])
               (reset! state false))
       :then (fn []
               (is (= [[:remove "a"]
                       [:remove "b"]
                       [:post-remove "b"]
                       [:post-remove "a"]]
                      @log)))}])))
