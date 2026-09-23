(ns threeagent.tsl.runtime-test
  "Graph building through the real macros. No GPU needed: these inspect the
   node objects TSL returns."
  (:require [cljs.test :refer-macros [deftest is testing]]
            ["three/tsl" :as t]
            [threeagent.tsl :as tsl :refer-macros [shader defshader shader-fn defshader-fn compute]]))

(defn- op [^js n] (.-op ^js (.-node n)))

(deftest numbers-stay-numbers
  (is (= 7 (shader (+ 1 (* 2 3)))))
  (is (= [0 1 2] (shader (vec (for [i (range 3)] (* i 1)))))))

(deftest nodes-become-graphs
  (let [n (shader (+ (uv) [0.5 0.5]))]
    (is (tsl/node? n))
    (is (= "+" (op n))))
  (testing "unary minus negates nodes"
    (is (tsl/node? (shader (- (uv)))))))

(deftest swizzle-vs-lookup
  (let [u (t/uniform (t/vec3 1 2 3))]
    (is (tsl/node? (shader (:xy u))))
    (is (= 5 (shader (:xy {:xy 5}))))))

(deftest cpu-conditionals
  (let [debug? false]
    (is (= 2 (shader (if debug? 1 2))))
    (is (tsl/node? (shader (if (> (uv) 0.5) 1 2))))))

(deftest short-circuit
  (is (nil? (shader (and nil (throw (js/Error. "evaluated"))))))
  (is (= 3 (shader (or nil 3)))))

(defshader-fn scale [v :vec3 k :float] :vec3
  (* v k))

(defshader-fn inline-scale [v k]
  (* v k))

(deftest shader-fn-calls
  (is (tsl/node? (scale [1 2 3] 2)))
  (is (tsl/node? (inline-scale (t/uv) 2)))
  (is (some? (tsl/fn-node-of scale))))

(deftest anonymous-shader-fn
  (let [f (shader-fn [a b] (+ a b))]
    (is (tsl/node? (f (t/uv) 1)))))

(defshader tinted [{:keys [tint]}]
  {:color (* tint [1 0.5 0.5])
   :opacity 0.5})

(deftest defshader-returns-outputs
  (let [out (tinted {:tint (t/uniform (t/vec3 1 1 1))})]
    (is (tsl/node? (:color out)))
    (is (= 0.5 (:opacity out)))))

(deftest statement-shaders-are-fn-calls
  (let [n (shader (let-var [acc 0]
                    (dotimes [i 4] (+= acc i))
                    acc))]
    (is (tsl/node? n))))

(deftest compute-nodes
  (let [buf (t/instancedArray 16 "float")
        c (compute {:count 16}
            (let [i instance-index]
              (set! (nth buf i) (+ (nth buf i) 1))))]
    (is (true? (.-isComputeNode ^js c)))))
