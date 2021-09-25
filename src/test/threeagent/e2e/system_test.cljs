(ns threeagent.e2e.system-test
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
  (on-entity-added [_ ctx id _obj _config]
    (swap! log conj {:event :added
                     :entity-id id
                     :context ctx}))
  (on-entity-removed [_ ctx id _obj _config]
    (swap! log conj {:event :removed
                     :entity-id id
                     :context ctx}))
  (tick [_ _]))

(defn root [state]
  [:object
   [:object {:id "a"
             :custom-system {}}
    [:object {:id "b"
              :custom-system {}}
     (when (:add-third? @state)
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

(deftest rerender-calls-remove-hook-test
  (let [log (atom #{})
        root-fn (fn []
                  [:object {:id "a"
                            :system {}}
                   [{:context :b}
                    [:object {:id "b"
                              :system {}}]]])
        my-system (->MySystem log)]
    (th/render root-fn @canvas
               {:systems {:system my-system}})
    (is (= #{{:event :added
              :entity-id "a"
              :context {}}
             {:event :added
              :entity-id "b"
              :context {:context :b}}}
           @log))
    ;; Ensure consistency across re-render
    (reset! log #{})
    (th/render root-fn @canvas
               {:systems {:system my-system}})
    (is (= 4 (count @log)))))


(deftest reactive-render-system-test
  (let [state (th/atom {})
        log (atom #{})
        my-system (->MySystem log)]
    (fixture/async-run! 
     [{:when (fn []
               (th/render (partial root state) @canvas
                          {:systems {:custom-system my-system}}))
       :then (fn []
               (is (= 2 (count @log))))}
      {:when (fn []
               (swap! state assoc :add-third? true))
       :then (fn []
               (is (= 3 (count @log)))
               (is (contains? (->> @log
                                   (map :entity-id)
                                   (set))
                             "c")))}
      {:when (fn []
               (reset! log #{})
               (swap! state assoc :add-third? false))
       :then (fn []
               (is (= 1 (count @log)))
               (is (= #{{:event :removed
                         :entity-id "c"
                         :context {}}}
                      @log)))}])))

(deftest rerender-child-component-system-lifecycle-test
  (let [state (th/atom true)
        log (atom #{})
        my-system (->MySystem log)]
    (fixture/async-run! 
      [{:when (fn []
                (th/render (partial root2 state) @canvas
                           {:systems {:custom-system my-system}}))
        :then (fn []
                (is (= #{{:event :added
                          :entity-id "a"
                          :context {}}}
                       @log)))}
       {:when (fn []
                (reset! log #{})
                (reset! state false))
        :then (fn []
                (is (= #{{:event :removed
                          :entity-id "a"
                          :context {}}}
                        @log)))}])))
