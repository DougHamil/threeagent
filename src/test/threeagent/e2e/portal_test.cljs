(ns threeagent.e2e.portal-test
  (:require [cljs.test :refer-macros [deftest is use-fixtures]]
            ["three" :as three]
            [threeagent.e2e.fixture :as fixture]
            [threeagent.threejs.util :refer [get-in-object]]
            [threeagent.core :as th]))

(defonce canvas (atom nil))

(use-fixtures :each (fixture/with-canvas canvas))

(defn- ->obj [name]
  (let [obj (three/Object3D.)]
    (set! (.-name obj) name)
    obj))

(defn- create-3js-obj
  ([path]
   (let [o (->obj (first path))]
     (create-3js-obj o (rest path))
     o))
  ([parent path]
   (let [[x & xs] path]
     (if x
       (let [o (->obj x)]
         (.add parent o)
         (create-3js-obj o xs)
         o)
       parent))))

(deftest unreactive-portal-test
  (let [nested-obj (create-3js-obj ["Spine" "Neck" "Head"])
        parent (get-in-object nested-obj ["Spine" "Neck"])
        root-fn (fn []
                  [:object
                   [:instance {:object nested-obj}
                    [:> ["Spine" "Neck"]
                     [:object {:id "a"}]
                     [:object {:id "b"}]]]])]
    (fixture/async-run!
     [{:when (fn []
               (th/render root-fn @canvas))
       :then (fn []
               (js/console.log nested-obj)
               (is (= 3 (count (.-children parent)))))}])))

(deftest reactive-portal-test
  (let [state (th/atom true)
        nested-obj (create-3js-obj ["Spine" "Neck" "Head"])
        parent (get-in-object nested-obj ["Spine" "Neck"])
        root-fn (fn []
                  [:object
                   [:instance {:object nested-obj}
                    [:> ["Spine" "Neck"]
                     (when @state
                       [:object {:id "a"}])
                     [:object {:id "b"}]]]])]
    (fixture/async-run!
     [{:when (fn []
               (th/render root-fn @canvas))
       :then (fn []
               (is (= 3 (count (.-children parent)))))}
      {:when (fn []
               (reset! state false))
       :then (fn []
               (is (= 2 (count (.-children parent)))))}])))

(deftest reactive-portal-drop-test
  (let [state (th/atom true)
        nested-obj (create-3js-obj ["Spine" "Neck" "Head"])
        parent (get-in-object nested-obj ["Spine" "Neck"])
        root-fn (fn []
                  [:object
                   [:instance {:object nested-obj}
                    (when @state
                      [:> ["Spine" "Neck"]
                       [:object {:id "a"}]
                       [:object {:id "b"}]])]])]
    (fixture/async-run!
     [{:when (fn []
               (th/render root-fn @canvas))
       :then (fn []
               (is (= 3 (count (.-children parent)))))}
      {:when (fn []
               (reset! state false))
       :then (fn []
               (is (= 1 (count (.-children parent)))))}
      {:when (fn []
               (reset! state true))
       :then (fn []
               (is (= 3 (count (.-children parent)))))}])))

(deftest reactive-portal-add-child-test
  (let [state (th/atom 0)
        nested-obj (create-3js-obj ["Spine" "Neck" "Head"])
        parent (get-in-object nested-obj ["Spine" "Neck"])
        get-parent (fn []
                     (aget (.-children parent) 1))
        root-fn (fn []
                  [:object
                   [:instance {:object nested-obj}
                    [:> ["Spine" "Neck"]
                     (for [i (range  @state)]
                       [:object {:id (str i)}])]]])]
    (fixture/async-run!
     [{:when (fn []
               (th/render root-fn @canvas))
       :then (fn []
                (is (= 1 (count (.-children parent)))))}
      {:when (fn []
               (reset! state 2))
       :then (fn []
               (is (= @state
                      (count (.-children (get-parent))))))}
      {:when (fn []
               (reset! state 1))
       :then (fn []
               (is (= @state
                      (count (.-children (get-parent))))))}])))
