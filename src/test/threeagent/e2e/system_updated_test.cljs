(ns threeagent.e2e.system-updated-test
  (:require [cljs.test :refer-macros [deftest is use-fixtures]]
            ["three/webgpu" :as three]
            [threeagent.e2e.fixture :as fixture]
            [threeagent.system :refer [ISystem]]
            [threeagent.entity :refer [IEntityType IUpdateableEntityType]]
            [threeagent.core :as th]))

(defonce canvas (atom nil))

(use-fixtures :each (fixture/with-canvas canvas))

(defrecord UpdateTrackingSystem [log]
  ISystem
  (init [_ _ctx])
  (destroy [_ _ctx])
  (on-entity-added [_ ctx id _obj config]
    (swap! log conj {:event :added
                     :entity-id id
                     :context ctx
                     :config config}))
  (on-entity-updated [_ ctx id _obj config]
    (swap! log conj {:event :updated
                     :entity-id id
                     :context ctx
                     :config config}))
  (on-entity-removed [_ ctx id _obj config]
    (swap! log conj {:event :removed
                     :entity-id id
                     :context ctx
                     :config config}))
  (tick [_ _]))

(deftype UpdateableEntity [state]
  IEntityType
  (create [_ _ {:keys [value]}]
    (let [obj (three/Object3D.)]
      (set! (.-testValue obj) value)
      (swap! state assoc :created true)
      obj))
  (destroy! [_ _ _ _]
    (swap! state assoc :destroyed true))
  IUpdateableEntityType
  (update! [_ _ ^three/Object3D obj {:keys [value]}]
    (set! (.-testValue obj) value)
    (swap! state update :update-count (fnil inc 0))))

(deftest on-entity-updated-called-for-updateable-entities-test
  (let [log (atom [])
        entity-state (atom {})
        scene-state (th/atom {:value "initial"})
        my-system (->UpdateTrackingSystem log)
        entity-types {:updateable-entity (UpdateableEntity. entity-state)}
        root-fn (fn []
                  [:updateable-entity {:id :my-entity
                                       :tracking-system (:value @scene-state)
                                       :value (:value @scene-state)}])]
    (fixture/async-run!
     [{:when (fn []
               (th/render root-fn @canvas {:systems {:tracking-system my-system}
                                          :entity-types entity-types}))
       :then (fn []
               (is (= [{:event :added
                        :entity-id :my-entity
                        :context {}
                        :config "initial"}]
                      @log))
               (is (:created @entity-state))
               (is (not (:destroyed @entity-state))))}
      {:when (fn []
               (reset! log [])
               (reset! scene-state {:value "updated"}))
       :then (fn []
               ;; Should call on-entity-updated, NOT on-entity-removed + on-entity-added
               (is (= [{:event :updated
                        :entity-id :my-entity
                        :context {}
                        :config "updated"}]
                      @log))
               (is (= 1 (:update-count @entity-state)))
               ;; Entity should not have been destroyed and recreated
               (is (not (:destroyed @entity-state))))}
      {:when (fn []
               (reset! log [])
               (reset! scene-state {:value "updated-again"}))
       :then (fn []
               (is (= [{:event :updated
                        :entity-id :my-entity
                        :context {}
                        :config "updated-again"}]
                      @log))
               (is (= 2 (:update-count @entity-state))))}])))

(deftest on-entity-updated-respects-context-test
  (let [log (atom [])
        entity-state (atom {})
        scene-state (th/atom {:value "initial"})
        my-system (->UpdateTrackingSystem log)
        entity-types {:updateable-entity (UpdateableEntity. entity-state)}
        root-fn (fn []
                  [:object
                   [{:my-context "value"}
                    [:updateable-entity {:id :my-entity
                                         :tracking-system (:value @scene-state)
                                         :value (:value @scene-state)}]]])]
    (fixture/async-run!
     [{:when (fn []
               (th/render root-fn @canvas {:systems {:tracking-system my-system}
                                          :entity-types entity-types}))
       :then (fn []
               (is (= [{:event :added
                        :entity-id :my-entity
                        :context {:my-context "value"}
                        :config "initial"}]
                      @log)))}
      {:when (fn []
               (reset! log [])
               (reset! scene-state {:value "updated"}))
       :then (fn []
               (is (= [{:event :updated
                        :entity-id :my-entity
                        :context {:my-context "value"}
                        :config "updated"}]
                      @log)))}])))

(deftest on-entity-updated-with-built-in-entities-test
  (let [log (atom [])
        scene-state (th/atom {:color "red"})
        my-system (->UpdateTrackingSystem log)
        root-fn (fn []
                  [:box {:id :my-box
                         :tracking-system (:color @scene-state)
                         :material {:color (:color @scene-state)}}])]
    (fixture/async-run!
     [{:when (fn []
               (th/render root-fn @canvas {:systems {:tracking-system my-system}}))
       :then (fn []
               (is (= [{:event :added
                        :entity-id :my-box
                        :context {}
                        :config "red"}]
                      @log)))}
      {:when (fn []
               (reset! log [])
               (reset! scene-state {:color "blue"}))
       :then (fn []
               ;; Built-in entities like :box implement IUpdateableEntityType
               (is (= [{:event :updated
                        :entity-id :my-box
                        :context {}
                        :config "blue"}]
                      @log)))}])))
