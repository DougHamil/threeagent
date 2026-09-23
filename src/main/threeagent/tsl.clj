(ns threeagent.tsl
  "Macros for writing TSL shaders as Clojure. See threeagent/tsl.cljs for the
   language reference and threeagent.tsl.compiler for the rewriting rules."
  (:require [threeagent.tsl.compiler :as c]))

(defmacro shader
  "Compile the body as shader code and return the resulting TSL node, or a map
   of nodes when the body returns a map literal.

   Bodies that use statements (`set!`, `+=`, `let-var`, `dotimes`, `for-range`,
   `while`, a node-conditioned `when`...) are wrapped in a TSL Fn."
  [& body]
  (c/expand-shader body &env))

(defmacro defshader
  "`defn` whose body is shader code. Typical use is a node material's shader:

     (defshader glow [{:keys [time tint]}]
       {:color (* tint (+ 0.5 (* 0.5 (sin time))))})"
  [& args]
  (c/expand-defshader args))

(defmacro shader-fn
  "A TSL Fn wrapped in a Clojure function.

     (shader-fn [a b] (* a b))                     ; inlined into the caller
     (shader-fn [a :float b :vec3] :vec3 (* a b))  ; emitted as a WGSL function
     (shader-fn [p :vec3] :void (set! ...))        ; statement function"
  [& args]
  (c/expand-shader-fn args &env &form))

(defmacro defshader-fn
  "`def` + `shader-fn`. Typed params produce a WGSL function named after the var."
  [& args]
  (c/expand-defshader-fn args &env &form))

(defmacro compute
  "A compute node running the body once per invocation.

     (compute {:count n :workgroup-size [64]}
       (let [i instance-index]
         (set! (nth positions i) (+ (nth positions i) (nth velocities i)))))"
  [& args]
  (c/expand-compute args &env &form))

(defmacro defcompute
  "`def` + `compute`."
  [& args]
  (c/expand-defcompute args &env &form))
