(ns threeagent.tsl
  "Write Three.js TSL shaders as Clojure.

   Macros (see threeagent/tsl.clj):
   - `shader`        compile an expression into a TSL node (or a map of nodes)
   - `defshader`     `defn` whose body is shader code, e.g. a material's shader
   - `shader-fn`     a TSL `Fn`, callable like a Clojure function
   - `defshader-fn`  `def` + `shader-fn`; typed params emit a named WGSL function
   - `compute`       a compute node: `(compute {:count n} body...)`
   - `defcompute`    `def` + `compute`

   Inside shader code, arithmetic, comparison and logic operators build nodes
   when any argument is a node and keep their Clojure meaning otherwise;
   `[a b c]` is a vec3; `(:xyz v)` swizzles; any three/tsl export can be called
   by its JS name or kebab-case name (`position-local`, `mx-noise-float`).

   The functions below are the runtime the macros compile to. They are public
   so the SCI integration can reach them, and usable directly."
  (:refer-clojure :exclude [mod rem inc dec min max not nth
                            bit-and bit-or bit-xor bit-not bit-shift-left bit-shift-right])
  (:require ["three/tsl" :as tsl-module]
            ["three/webgpu" :refer [JoinNode]])
  (:require-macros [threeagent.tsl]))

(def T
  "The three/tsl module."
  tsl-module)

(defn node?
  "True for TSL nodes."
  [x]
  (and (some? x) (true? (unchecked-get x "isNode"))))

;; ---------------------------------------------------------------------------
;; three/tsl access
;; ---------------------------------------------------------------------------

(defn- export [n]
  (let [f (unchecked-get T n)]
    (when (undefined? f)
      (throw (js/Error. (str "three/tsl has no export named " n))))
    f))

(defn $get
  "The three/tsl export named `n`."
  [n]
  (export n))

(defn $
  "Call the three/tsl export named `n`.

   Uses Reflect.apply: many exports are `Fn` Proxies whose `get` trap turns
   `f.call(null, a)` into `FnNode.call(null, a)`, shifting every argument."
  ([n] (js/Reflect.apply (export n) nil #js []))
  ([n a] (js/Reflect.apply (export n) nil #js [a]))
  ([n a b] (js/Reflect.apply (export n) nil #js [a b]))
  ([n a b c] (js/Reflect.apply (export n) nil #js [a b c]))
  ([n a b c d] (js/Reflect.apply (export n) nil #js [a b c d]))
  ([n a b c d & more] (js/Reflect.apply (export n) nil (to-array (list* a b c d more)))))

;; ---------------------------------------------------------------------------
;; Vector literals
;; ---------------------------------------------------------------------------

(defn join
  "`[a b c]` in shader code. All numbers: `vecN` of that many components.
   With nodes: a JoinNode, whose size is the total component count, resolved
   when the shader builds - so `[pos 1]` with a vec3 `pos` is a vec4."
  [& xs]
  (if (some node? xs)
    ($ "nodeObject" (JoinNode. (to-array (map #(if (node? %) % ($ "float" %)) xs))))
    (case (count xs)
      2 ($ "vec2" (first xs) (second xs))
      3 ($ "vec3" (first xs) (second xs) (cljs.core/nth xs 2))
      4 ($ "vec4" (first xs) (second xs) (cljs.core/nth xs 2) (cljs.core/nth xs 3)))))

;; ---------------------------------------------------------------------------
;; Operators: nodes in, node out; numbers in, number out
;; ---------------------------------------------------------------------------

(defn- node2? [a b] (or (node? a) (node? b)))

(defn- fractional? [x]
  (and (number? x) (cljs.core/not (js/Number.isInteger x))))

(defn- promote
  "A node as the float type of the same width (int -> float, ivec3 -> vec3);
   float nodes are unchanged."
  [n]
  ($ "convert" n "float|vec2|vec3|vec4|mat3|mat4"))

(defn- binop
  "TSL types `node op number` as the node's type, so with an int node
   `(* i 0.25)` would compile to `i * 0`. When a node meets a fractional
   number, the node is promoted to float first."
  [tsl-name cpu-fn]
  (fn [a b]
    (cond
      (cljs.core/not (node2? a b)) (cpu-fn a b)
      (fractional? b) ($ tsl-name (promote a) ($ "float" b))
      (fractional? a) ($ tsl-name ($ "float" a) (promote b))
      :else ($ tsl-name a b))))

(defn- variadic [op2 identity-val unary]
  (fn
    ([] identity-val)
    ([a] (unary a))
    ([a b] (op2 a b))
    ([a b & more] (reduce op2 (op2 a b) more))))

(def ^:private add2 (binop "add" +))
(def ^:private sub2 (binop "sub" -))
(def ^:private mul2 (binop "mul" *))
(def ^:private div2 (binop "div" /))

(def add (variadic add2 0 identity))
(def sub (variadic sub2 0 #(if (node? %) ($ "negate" %) (- %))))
(def mul (variadic mul2 1 identity))
(def div (variadic div2 1 #(if (node? %) ($ "reciprocal" %) (/ %))))

(def mod
  "Node: WGSL `%`. Numbers: `cljs.core/mod`."
  (binop "mod" cljs.core/mod))

(def rem (binop "mod" cljs.core/rem))

(defn inc [x] (add2 x 1))
(defn dec [x] (sub2 x 1))

(def ^:private min2 (binop "min" cljs.core/min))
(def ^:private max2 (binop "max" cljs.core/max))
(def min (variadic min2 nil identity))
(def max (variadic max2 nil identity))

(defn- comparison [tsl-name cpu-fn]
  (let [op2 (binop tsl-name cpu-fn)]
    (fn
      ([_] true)
      ([a b] (op2 a b))
      ([a b & more]
       (let [args (list* a b more)]
         (if (some node? args)
           (reduce #($ "and" %1 %2) (map op2 args (rest args)))
           (apply cpu-fn args)))))))

(def lt (comparison "lessThan" <))
(def gt (comparison "greaterThan" >))
(def lte (comparison "lessThanEqual" <=))
(def gte (comparison "greaterThanEqual" >=))
(def eq (comparison "equal" =))
(def neq (comparison "notEqual" not=))

(defn not [x]
  (if (node? x) ($ "not" x) (cljs.core/not x)))

(def bit-and (variadic (binop "bitAnd" cljs.core/bit-and) -1 identity))
(def bit-or (variadic (binop "bitOr" cljs.core/bit-or) 0 identity))
(def bit-xor (variadic (binop "bitXor" cljs.core/bit-xor) 0 identity))
(defn bit-not [x] (if (node? x) ($ "bitNot" x) (cljs.core/bit-not x)))
(def bit-shift-left (binop "shiftLeft" cljs.core/bit-shift-left))
(def bit-shift-right (binop "shiftRight" cljs.core/bit-shift-right))

(defn nth
  "Node: `.element(i)` (arrays, buffers, matrix columns). Otherwise `cljs.core/nth`."
  ([coll i]
   (if (node? coll) (.element ^js coll i) (cljs.core/nth coll i)))
  ([coll i not-found]
   (cljs.core/nth coll i not-found)))

(defn kw
  "`(:k x)` in shader code: a property (swizzle) on nodes, a lookup otherwise."
  [x k]
  (if (node? x) (unchecked-get x (name k)) (get x k)))

;; ---------------------------------------------------------------------------
;; Short-circuiting logic and conditionals (thunk arguments)
;; ---------------------------------------------------------------------------

(defn and*
  "Evaluates thunks left to right with Clojure short-circuiting until a node
   shows up; from there on the rest are combined with TSL `and`."
  [& thunks]
  (loop [thunks thunks acc true]
    (if-let [t (first thunks)]
      (let [v (t)]
        (cond
          (node? v) (reduce (fn [a t] ($ "and" a (t))) v (rest thunks))
          (cljs.core/not v) v
          :else (recur (rest thunks) v)))
      acc)))

(defn or*
  [& thunks]
  (loop [thunks thunks acc nil]
    (if-let [t (first thunks)]
      (let [v (t)]
        (cond
          (node? v) (reduce (fn [a t] ($ "or" a (t))) v (rest thunks))
          v v
          :else (recur (rest thunks) v)))
      acc)))

(defn- void [thunk] (fn [& _] (thunk) nil))

(defn if*
  "Node test: TSL `select` for expressions, `If().Else()` for statements.
   CPU test: plain Clojure `if`."
  [test then else stmt?]
  (if (node? test)
    (if (or stmt? (nil? else))
      (let [^js branch ($ "If" test (void then))]
        (when else (.Else branch (void else)))
        nil)
      ($ "select" test (then) (else)))
    (if test (then) (when else (else)))))

(defn cond*
  "`clauses` alternates test thunks (or :else) and body thunks. CPU tests
   short-circuit as in Clojure; node tests become a select chain (expressions)
   or an If/ElseIf/Else chain (statements)."
  [stmt? clauses]
  (let [pairs (partition 2 clauses)
        eval-test (fn [t] (if (keyword? t) true (t)))]
    (loop [pairs pairs]
      (when-let [[t body] (first pairs)]
        (let [v (eval-test t)]
          (cond
            (node? v)
            (let [tail (rest pairs)]
              (if stmt?
                (loop [chain ($ "If" v (void body)) tail tail]
                  (if-let [[t body] (first tail)]
                    (let [^js chain chain
                          tv (eval-test t)]
                      (cond
                        (node? tv) (recur (.ElseIf chain tv (void body)) (rest tail))
                        tv (do (.Else chain (void body)) nil)
                        :else (recur chain (rest tail))))
                    nil))
                ($ "select" v (body) (cond* false (mapcat identity tail)))))

            v (body)
            :else (recur (rest pairs))))))))

;; ---------------------------------------------------------------------------
;; Statements
;; ---------------------------------------------------------------------------

(defn assign!
  "`(set! target value)` in shader code."
  [^js target value]
  (.assign target value))

(defn op-assign!
  "`(+= target value)` and friends. `method` is e.g. \"addAssign\"."
  [method target value]
  (.call (unchecked-get target method) target value))

(defn var!
  "A mutable shader variable initialised to `init` (`let-var`)."
  [init]
  ($ "Var" init))

(defn loop-bound
  "A loop bound: nodes are stored in a variable, evaluated once before the
   loop (as with dotimes); numbers pass through. TSL builds a Loop's bounds
   without the analysis that records where nodes are used, so a node placed
   on the statement stack when created, like an atomic, would be emitted as a
   bare statement and the bound left empty: the loop would run 0 times."
  [x]
  (if (node? x) (var! x) x))

(defn loop-range
  "A TSL Loop over `name` from `start` towards `end` by `step`. `body` gets the
   loop index node. Node bounds are evaluated once, before the loop."
  [name start end step body]
  (let [start (loop-bound start)
        end (loop-bound end)
        step (loop-bound step)
        down? (and (number? step) (neg? step))
        opts #js {:start start
                  :end end
                  :type "int"
                  :name name
                  :condition (if down? ">" "<")
                  :update (if (number? step)
                            (js/Math.abs step)
                            (fn [^js inputs & _]
                              (let [^js i (unchecked-get inputs name)]
                                (.addAssign i step))
                              nil))}]
    ($ "Loop" opts (fn [^js inputs & _]
                     (body (unchecked-get inputs name))
                     nil))
    nil))

(defn loop-while
  [test body]
  ($ "Loop" test body)
  nil)

;; ---------------------------------------------------------------------------
;; Fn / compute
;; ---------------------------------------------------------------------------

(defn fn-node
  "`tsl.Fn(f, layout)`. The result is a JS Proxy: call it with `call-fn`,
   never as a Clojure function (IFn dispatch breaks it in advanced builds)."
  [f layout]
  ($ "Fn" f layout))

(defn call-fn
  [^js fn-node & args]
  (.apply (.-call fn-node) fn-node (to-array args)))

(defn fn-arg
  "Parameter `i` (named `n`) of a TSL Fn. Without a layout TSL passes an
   array-like; with one, an object keyed by parameter name."
  [^js inputs i n]
  (if (undefined? (.-length inputs))
    (unchecked-get inputs n)
    (aget inputs i)))

(defn layout
  [fn-name return-type inputs]
  #js {:name fn-name
       :type return-type
       :inputs (into-array (map (fn [[n t]] #js {:name n :type t}) inputs))})

(defn shader-fn*
  "Wrap a TSL Fn in a Clojure function of `arity` args. The Fn itself is
   available as `(fn-node-of f)`."
  [f layout arity]
  (let [node (fn-node f layout)
        wrapper (case arity
                  0 (fn [] (call-fn node))
                  1 (fn [a] (call-fn node a))
                  2 (fn [a b] (call-fn node a b))
                  3 (fn [a b c] (call-fn node a b c))
                  (fn [& args] (apply call-fn node args)))]
    (with-meta wrapper {::fn-node node})))

(defn fn-node-of
  "The underlying TSL Fn of a `shader-fn`."
  [shader-fn]
  (::fn-node (meta shader-fn)))

(defn compute*
  [f count workgroup-size]
  (let [kernel (call-fn (fn-node f nil))]
    (if workgroup-size
      ($ "compute" kernel count (clj->js workgroup-size))
      ($ "compute" kernel count))))
