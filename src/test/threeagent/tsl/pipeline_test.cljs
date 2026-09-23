(ns threeagent.tsl.pipeline-test
  (:require [cljs.test :refer-macros [deftest is testing]]
            ["three/tsl" :as t]
            [threeagent.tsl :as tsl]
            [threeagent.tsl.pipeline :as pipeline]))

(defn- fake-pass [label]
  #js {:getTextureNode (fn [n] (doto (t/uniform 1) (aset "label" (str label "/" n))))})

(def passes {:world (fake-pass "world") :ui (fake-pass "ui")})

(deftest scene-outputs
  (is (= "world/output" (.-label ^js (pipeline/build :world passes))))
  (is (= "world/depth" (.-label ^js (pipeline/build [:pass :world "depth"] passes))))
  (is (thrown-with-msg? js/Error #"unknown scene" (pipeline/build :nope passes))))

(deftest effects
  (testing "built-ins"
    (is (tsl/node? (pipeline/build [:add :world :ui] passes))))
  (testing "fn heads get input nodes and opts"
    (let [seen (atom nil)
          effect (fn [inputs opts] (reset! seen [(mapv #(.-label ^js %) inputs) opts]) (first inputs))]
      (pipeline/build [effect {:strength 2} :world :ui] passes)
      (is (= [["world/output" "ui/output"] {:strength 2}] @seen))))
  (testing "registered effects"
    (pipeline/register-effect! ::double (fn [[x] _] (tsl/mul x 2)))
    (is (tsl/node? (pipeline/build [::double :world] passes))))
  (testing "unknown effects fail loudly"
    (is (thrown-with-msg? js/Error #"Unknown render pipeline effect" (pipeline/build [:nope :world] passes)))))

(deftest shared-subforms
  (let [calls (atom 0)
        effect (fn [[x] _] (swap! calls inc) (tsl/mul x 2))
        sub [effect :world]]
    (pipeline/build [:add sub [:mul sub 0.5]] passes)
    (is (= 1 @calls) "equal subforms build once")))

(deftest pipeline-fn
  (let [f (fn [_] :node)]
    (is (identical? f (pipeline/->pipeline-fn f))))
  (is (fn? (pipeline/->pipeline-fn [:add :world :ui]))))
