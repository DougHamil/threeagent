(ns threeagent.e2e.light-default-position-test
  (:require [cljs.test :refer-macros [deftest is use-fixtures]]
            [threeagent.e2e.fixture :as fixture]
            [threeagent.core :as th]))

(defonce canvas (atom nil))

(use-fixtures :each (fixture/with-canvas canvas))

(defn- position [^js obj]
  (vec (.toArray (.-position obj))))

(deftest directional-lights-default-to-straight-up
  (let [state (th/atom {:intensity 1})
        lights (atom {})
        added (fn [k] #(swap! lights assoc k %))
        root-fn (fn []
                  (let [{:keys [intensity]} @state]
                    [:object
                     [:hemisphere-light {:intensity intensity :on-added (added :hemisphere)}]
                     [:directional-light {:intensity intensity :on-added (added :directional)}]
                     [:spot-light {:intensity intensity :on-added (added :spot)}]
                     [:point-light {:intensity intensity :on-added (added :point)}]
                     [:hemisphere-light {:position [3 4 5] :on-added (added :placed)}]]))
        check (fn []
                (is (= [0 1 0] (position (:hemisphere @lights)))
                    "a hemisphere light without :position keeps Three's up")
                (is (= [0 1 0] (position (:directional @lights))))
                (is (= [0 1 0] (position (:spot @lights))))
                (is (= [0 0 0] (position (:point @lights)))
                    "a point light has no direction; the origin is fine")
                (is (= [3 4 5] (position (:placed @lights)))
                    "an explicit :position still wins"))]
    (fixture/async-run!
     [{:when #(th/render root-fn @canvas)
       :then check}
      {:when #(swap! state assoc :intensity 2)
       :then (fn []
               (is (= 2 (.-intensity ^js (:hemisphere @lights))))
               (check))}])))
