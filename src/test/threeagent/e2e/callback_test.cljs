(ns threeagent.e2e.callback-test
  (:require [cljs.test :refer-macros [deftest is use-fixtures]]
            [threeagent.e2e.fixture :as fixture]
            [threeagent.core :as th]))

(defonce canvas (atom nil))

(use-fixtures :each (fixture/with-canvas canvas))

(deftest callback-test
  (let [ref-state (atom nil)
        on-added-state (atom nil)
        on-removed-state (atom nil)
        state (th/atom true)
        root-fn (fn []
                  [:object
                   (when @state
                     [:object {:ref #(reset! ref-state %)
                               :on-removed #(reset! on-removed-state %)
                               :on-added #(reset! on-added-state %)}])])]
    (fixture/async-run!
     [{:when #(th/render root-fn @canvas)
       :then (fn []
               (is (some? @ref-state))
               (is (some? @on-added-state))
               (is (= @ref-state @on-added-state))
               (is (nil? @on-removed-state)))}
      {:when #(reset! state false)
       :then (fn []
               (is (some? @on-removed-state)))}])))

(deftest on-updated-callback-test
  (let [on-added-state (atom nil)
        on-updated-state (atom nil)
        on-updated-count (atom 0)
        state (th/atom {:color "red"})
        root-fn (fn []
                  [:box {:material {:color (:color @state)}
                         :on-added #(reset! on-added-state %)
                         :on-updated (fn [obj]
                                       (reset! on-updated-state obj)
                                       (swap! on-updated-count inc))}])]
    (fixture/async-run!
     [{:when #(th/render root-fn @canvas)
       :then (fn []
               (is (some? @on-added-state))
               (is (nil? @on-updated-state))
               (is (= 0 @on-updated-count)))}
      {:when #(reset! state {:color "blue"})
       :then (fn []
               (is (some? @on-updated-state))
               (is (= @on-added-state @on-updated-state))
               (is (= 1 @on-updated-count)))}
      {:when #(reset! state {:color "green"})
       :then (fn []
               (is (= 2 @on-updated-count)))}])))
