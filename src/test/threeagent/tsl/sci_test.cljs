(ns threeagent.tsl.sci-test
  "Shader code evaluated by SCI through threeagent.tsl.sci, as the live docs
   do. Covers the portable-compiler contract: the same expansions work
   without the ClojureScript analyzer."
  (:require [cljs.test :refer-macros [deftest is testing]]
            [sci.core :as sci]
            ["three/tsl" :as t]
            [threeagent.tsl :as tsl]
            [threeagent.tsl.sci :as tsl-sci]))

(defn- ctx []
  (sci/init {:namespaces {'threeagent.tsl (merge (sci/copy-ns threeagent.tsl (sci/create-ns 'threeagent.tsl))
                                                 tsl-sci/macros)}
             :classes {'js js/globalThis :allow :all}
             :js-libs {"three/tsl" t}}))

(defn- ev [code]
  (sci/eval-string* (ctx) (str "(require '[threeagent.tsl :refer [shader defshader shader-fn defshader-fn compute]])\n" code)))

(deftest sci-expressions
  (is (= 7 (ev "(shader (+ 1 (* 2 3)))")))
  (is (tsl/node? (ev "(shader (+ (uv) [0.5 0.5]))")))
  (is (tsl/node? (ev "(let [length 2] (shader (* (uv) length)))")) "SCI locals shadow TSL names"))

(deftest sci-definitions
  (testing "defshader + defshader-fn"
    (let [out (ev "(defshader-fn scale [v :vec3 k :float] :vec3 (* v k))
                   (defshader tinted [{:keys [tint]}] {:color (scale tint 2) :opacity 0.5})
                   (tinted {:tint [1 0 0]})")]
      (is (tsl/node? (:color out)))
      (is (= 0.5 (:opacity out)))))
  (testing "statements get a Fn stack"
    (is (tsl/node? (ev "(shader (let-var [acc 0] (dotimes [i 4] (+= acc (* i 0.25))) acc))"))))
  (testing "compute"
    (is (true? (.-isComputeNode ^js (ev "(let [buf (shader (instanced-array 8 \"float\"))]
                                          (compute {:count 8}
                                            (set! (nth buf instance-index) 1)))")))))
  (testing "compile errors surface"
    (is (thrown-with-msg? js/Error #"2-4 elements" (ev "(shader [1])")))))
