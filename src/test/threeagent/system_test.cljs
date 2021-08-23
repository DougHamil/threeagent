(ns threeagent.system-test
  (:require [cljs.test :refer-macros [deftest is testing async]]
            [threeagent.system :refer [ISystem]]
            [threeagent.core :as th]))

(def state (th/atom {}))

(defrecord MySystem [sys-state]
  ISystem
  (on-entity-added [_ key _obj config]
    (swap! sys-state conj config))
  (on-entity-removed [_ key _obj config]
    (swap! sys-state disj config))
  (tick [_ _]))

(defonce sys-state (atom #{}))
(defonce my-system (->MySystem sys-state))

(defn root []
  [:object
    [:object {:custom-system {:value "a"}}
      [:object {:custom-system {:value "b"}}]
      (when (:add-third? @state)
        [:object {:custom-system {:value "c"}}])]])

(deftest persistent-custom-system-test
  (th/render root (js/document.getElementById "test-root")
              {:systems {:custom-system my-system}})
  (is (= 2 (count @sys-state)))
  ;; Ensure consistency across re-render
  (th/render root (js/document.getElementById "test-root")
              {:systems {:custom-system my-system}})
  (is (= 2 (count @sys-state))))

(deftest renewed-system-test
  (let [sys-state (atom #{})
        my-system (->MySystem sys-state)]
    (th/render root (js/document.getElementById "test-root")
                {:systems {:custom-system my-system}})
    (is (= 2 (count @sys-state)))
    (is (contains? @sys-state {:value "b"}))
    (is (contains? @sys-state {:value "a"}))
    ;; Ensure consistency across re-render
    (th/render root (js/document.getElementById "test-root")
                {:systems {:custom-system my-system}})
    (is (= 2 (count @sys-state)))))

(deftest reactive-render-system-test
  (async done
   (let [sys-state (atom #{})
         my-system (->MySystem sys-state)]
     (th/render root (js/document.getElementById "test-root")
                {:systems {:custom-system my-system}})
     (is (= 2 (count @sys-state)))
     (swap! state assoc :add-third? true)
     ;; Wait for re-render
     (js/setTimeout (fn []
                      (is (= 3 (count @sys-state)))
                      (is (contains? @sys-state {:value "c"}))
                      (done))
                    500))))
