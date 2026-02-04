(ns threeagent.e2e.multi-system-test
  (:require [cljs.test :refer-macros [deftest is use-fixtures testing]]
            [threeagent.e2e.fixture :as fixture]
            [threeagent.system :refer [ISystem]]
            [threeagent.core :as th]))

(defonce canvas (atom nil))

(use-fixtures :each (fixture/with-canvas canvas))

(defrecord AttachStateSystem [init-state sys-state]
  ISystem
  (init [_ _ctx]
    (swap! init-state conj :attach))
  (destroy [_ _ctx])
  (on-entity-added [_ _ _id ^js obj _config]
    (set! (.-theState obj) sys-state))
  (on-entity-updated [_ _ _id ^js _obj _config])
  (on-entity-removed [_ _ _id ^js obj _config]
    #(set! (.-theState obj) nil))
  (tick [_ _]))

(defrecord UseStateSystem [init-state]
  ISystem
  (init [_ _ctx]
    #(swap! init-state conj :use))
  (destroy [_ _ctx])
  (on-entity-added [_ _ _ ^js obj {:keys [entity-id]}]
    (let [state (.-theState obj)]
      #(swap! state conj entity-id)))
  (on-entity-updated [_ _ _ ^js _obj _config])
  (on-entity-removed [_ _ _ ^js obj {:keys [entity-id]}]
    (let [state (.-theState obj)]
      #(swap! state disj entity-id)))
  (tick [_ _]))

(deftest after-method-test []
  (testing "any fn returned from a method is invoked after all other systems have executed"
    (let [sys-state (atom #{})
          init-state (atom [])
          state (th/atom true)
          systems {:use-state (->UseStateSystem init-state)
                   :attach-state (->AttachStateSystem init-state sys-state)}
          root-fn (fn []
                    [:object
                     (when @state
                       [:object {:attach-state {}
                                 :use-state {:entity-id "root"}}])])]
      (fixture/async-run! [{:when (fn []
                                    (th/render root-fn @canvas {:systems systems}))
                            :then (fn []
                                    (is (= [:attach :use] @init-state))
                                    (is (= #{"root"} @sys-state)))}
                           {:when (fn []
                                    (reset! state false))
                            :then (fn []
                                    (is (= #{} @sys-state)))}]))))

