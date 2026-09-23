(ns threeagent.tsl.compiler-test
  "Expansion tests. The compiler is .cljc, so it runs here at runtime exactly
   as it does inside the macros."
  (:require [cljs.test :refer-macros [deftest is testing]]
            [threeagent.tsl.compiler :as c]))

(defn- expand [form & [locals]]
  (c/compile-form form (set locals)))

(deftest kebab-names
  (is (= "position-local" (c/kebab "positionLocal")))
  (is (= "two-pi" (c/kebab "TWO_PI")))
  (is (= "mx-noise-float" (c/kebab "mx_noise_float")))
  (is (= "s-rgb-transfer-eotf" (c/kebab "sRGBTransferEOTF"))))

(deftest tsl-symbols
  (testing "kebab and JS spellings both resolve"
    (is (= '(threeagent.tsl/$get "positionLocal") (expand 'position-local)))
    (is (= '(threeagent.tsl/$get "positionLocal") (expand 'positionLocal)))
    (is (= '(threeagent.tsl/$ "mix" a b 0.5) (expand '(mix a b 0.5)))))
  (testing "locals shadow TSL names"
    (is (= 'length (expand 'length '[length])))
    (is (= '(length x) (expand '(length x) '[length]))))
  (testing "CPU-colliding names keep their Clojure meaning"
    (is (= '(range 3) (expand '(range 3)))))
  (testing "unknown and qualified symbols pass through"
    (is (= '(my-helper x) (expand '(my-helper x))))
    (is (= 'foo/length (expand 'foo/length)))))

(deftest operators
  (is (= '(threeagent.tsl/add a b c) (expand '(+ a b c))))
  (is (= '(threeagent.tsl/sub a) (expand '(- a))))
  (is (= '(threeagent.tsl/lt a b) (expand '(< a b))))
  (is (= 'threeagent.tsl/add (expand '+)) "operators as values")
  (is (= '(threeagent.tsl/nth buf i) (expand '(nth buf i)))))

(deftest literals
  (is (= '(threeagent.tsl/$ "vec3" 1 0 0) (expand '[1 0 0])))
  (is (= '(threeagent.tsl/$ "vec2" (threeagent.tsl/$get "uv") 0) (expand '[uv 0])))
  (is (= {:color '(threeagent.tsl/$ "vec3" 1 1 1)} (expand '{:color [1 1 1]})))
  (is (= '[1] (expand '(clj [1]))))
  (is (thrown-with-msg? js/Error #"2-4 elements" (expand '[1]))))

(deftest swizzles-and-interop
  (is (= '(threeagent.tsl/kw v :xyz) (expand '(:xyz v))))
  (is (= '(.-x (threeagent.tsl/$ "uv")) (expand '(.-x (uv)))))
  (is (= '(.toVar (threeagent.tsl/$ "vec3" 0 0 0)) (expand '(.toVar [0 0 0])))))

(deftest threading
  (is (= '(threeagent.tsl/$ "mix" (threeagent.tsl/add x 0.5) b c)
         (expand '(-> x (+ 0.5) (mix b c)))))
  (is (= '(threeagent.tsl/kw (threeagent.tsl/$ "uv") :x)
         (expand '(-> (uv) :x)))))

(deftest binding-forms
  (testing "let values compile, patterns do not, bound names shadow"
    (is (= '(let [length (threeagent.tsl/$ "vec2" 1 2)] length)
           (expand '(let [length [1 2]] length)))))
  (testing "for/doseq keep their binding vectors"
    (is (= '(for [i (range 3) :let [k (threeagent.tsl/mul i 2)]] (threeagent.tsl/mul k x))
           (expand '(for [i (range 3) :let [k (* i 2)]] (* k x))))))
  (testing "fn params shadow"
    (is (= '(fn [uv] uv) (expand '(fn [uv] uv))))))

(deftest conditionals
  (is (= '(threeagent.tsl/if* (threeagent.tsl/gt d 0.5) (fn [& _] a) (fn [& _] b) false)
         (expand '(if (> d 0.5) a b))))
  (is (= '(threeagent.tsl/if* c (fn [& _] (threeagent.tsl/assign! x 1)) nil true)
         (expand '(when c (set! x 1)))))
  (is (= '(threeagent.tsl/and* (fn [& _] a) (fn [& _] b))
         (expand '(and a b)))))

(deftest statements
  (is (= '(threeagent.tsl/op-assign! "addAssign" acc 1) (expand '(+= acc 1))))
  (is (= '(threeagent.tsl/assign! (threeagent.tsl/kw v :x) 1) (expand '(set! (:x v) 1))))
  (is (= '(threeagent.tsl/assign! (.-xy v) 1) (expand '(set! (.-xy v) 1))) "swizzle property assigns")
  (is (= '(set! (.-value u) 1) (expand '(set! (.-value u) 1))) "plain property stays a CPU set!")
  (is (= '(let [acc (threeagent.tsl/var! 0)] acc) (expand '(let-var [acc 0] acc))))
  (is (= '(threeagent.tsl/loop-range "my_i" 0 8 1 (fn [my-i & _] (threeagent.tsl/$ "sample" my-i) nil))
         (expand '(dotimes [my-i 8] (sample my-i))))))

(deftest shader-wrapping
  (testing "pure expressions are returned as is"
    (is (= '(threeagent.tsl/mul a 2) (c/compile-shader '((* a 2)) #{}))))
  (testing "statements get a Fn stack"
    (is (= 'threeagent.tsl/call-fn (first (c/compile-shader '((let-var [a 0] (+= a 1) a)) #{})))))
  (testing "maps of outputs cannot hold statements"
    (is (thrown-with-msg? js/Error #"map of outputs"
                          (c/compile-shader '((let-var [a 0] {:color a})) #{})))))

(deftest shader-fns
  (let [form (c/expand-defshader-fn '(scale [v :vec3 k :float] :vec3 (* v k)) {} nil)
        [_ fname [ctor _ layout arity]] form]
    (is (= 'scale fname))
    (is (= 'threeagent.tsl/shader-fn* ctor))
    (is (= '(threeagent.tsl/layout "scale" "vec3" [["v" "vec3"] ["k" "float"]]) layout))
    (is (= 2 arity)))
  (is (thrown-with-msg? js/Error #"return type"
                        (c/expand-shader-fn '([v :vec3] (* v 2)) {} nil))))

(deftest env-locals
  (is (= #{'a} (c/env-locals {:locals {'a {}} :ns 'x})) "ClojureScript analyzer env")
  (is (= #{'a 'b} (c/env-locals {'a 1 'b 2})) "SCI / Clojure env"))
