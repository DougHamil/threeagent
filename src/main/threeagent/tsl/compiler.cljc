(ns threeagent.tsl.compiler
  "Rewrites Clojure forms into code that builds a TSL node graph.

   Pure form -> form functions with no dependency on the ClojureScript
   analyzer, so the same compiler backs both the shadow-cljs macros in
   `threeagent.tsl` and the SCI macros in `threeagent.tsl.sci`.

   The emitted code only calls plain functions in `threeagent.tsl` (no `^js`
   interop), which keeps it valid under advanced compilation and in SCI.

   Symbol resolution inside shader code:
   - lexical locals (from `&env` and bindings inside the form) always win
   - operators and special forms (`+`, `<`, `if`, `set!`, `dotimes`, ...) are
     rewritten to TSL-aware runtime calls
   - names exported by three/tsl, in their JS spelling (`positionLocal`) or
     kebab-case (`position-local`), become TSL references
   - everything else passes through untouched, as does `(clj ...)`"
  (:require [clojure.string :as str]
            [threeagent.tsl.exports :as exports]))

;; ---------------------------------------------------------------------------
;; TSL name table
;; ---------------------------------------------------------------------------

(defn kebab
  "positionLocal -> position-local, TWO_PI -> two-pi, mx_noise_float -> mx-noise-float"
  [js-name]
  (-> js-name
      (str/replace #"([a-z])([A-Z])" "$1-$2")
      (str/replace #"([A-Z]+)([A-Z][a-z])" "$1-$2")
      (str/replace "_" "-")
      (str/lower-case)))

(def ^:private cpu-names
  "TSL exports whose kebab spelling collides with clojure.core functions that
   shader-building code commonly uses on the CPU. They keep their Clojure
   meaning; reach the TSL version with `(threeagent.tsl/$ \"range\" ...)`."
  #{"range" "hash" "print" "array"})

(def ^:private aliases
  {"atan2" "atan"
   "dfdx" "dFdx"
   "dfdy" "dFdy"})

(def tsl-names
  "symbol -> three/tsl export name"
  (reduce (fn [m n]
            (let [k (kebab n)]
              (cond
                (contains? cpu-names k) m
                (contains? m (symbol k)) (assoc m (symbol n) n)
                :else (assoc m (symbol n) n (symbol k) n))))
          (into {} (map (fn [[a n]] [(symbol a) n])) aliases)
          (sort exports/names)))

;; ---------------------------------------------------------------------------
;; Operators with CPU/TSL dual dispatch
;; ---------------------------------------------------------------------------

(def ^:private rt 'threeagent.tsl)

(defn- rt-sym [n] (symbol (name rt) (name n)))

(def ^:private operators
  "Symbols replaced by runtime functions that build nodes when any argument is
   a node and fall back to Clojure semantics otherwise."
  {'+ 'add, '- 'sub, '* 'mul, '/ 'div
   'mod 'mod, 'rem 'rem, 'inc 'inc, 'dec 'dec
   'min 'min, 'max 'max
   '< 'lt, '> 'gt, '<= 'lte, '>= 'gte, '= 'eq, '== 'eq, 'not= 'neq
   'not 'not
   'bit-and 'bit-and, 'bit-or 'bit-or, 'bit-xor 'bit-xor, 'bit-not 'bit-not
   'bit-shift-left 'bit-shift-left, 'bit-shift-right 'bit-shift-right
   'nth 'nth})

(def ^:private assign-ops
  {'+= "addAssign", '-= "subAssign", '*= "mulAssign",
   '%= "modAssign"})

(def ^:private statement-heads
  "Forms that only make sense inside a TSL Fn stack."
  (into #{'set! 'let-var 'dotimes 'for-range 'while
          'break 'Break 'continue 'Continue 'discard 'Discard 'return 'Return}
        (keys assign-ops)))

(def ^:private swizzle-re #"[xyzw]{1,4}|[rgba]{1,4}|[stpq]{1,4}")

(defn- swizzle? [s] (boolean (re-matches swizzle-re s)))

;; ---------------------------------------------------------------------------
;; Helpers
;; ---------------------------------------------------------------------------

(defn- error [msg form]
  (throw (ex-info (str "tsl: " msg "\n  in: " (pr-str form)) {:form form})))

(defn- plain-symbol? [x]
  (and (symbol? x) (nil? (namespace x))))

(defn- head [form]
  (when (seq? form) (first form)))

(defn pattern-syms
  "Every symbol bound by a destructuring pattern."
  [pattern]
  (cond
    (plain-symbol? pattern) (if (= '& pattern) #{} #{pattern})
    (symbol? pattern) #{(symbol (name pattern))}
    (map? pattern) (reduce-kv (fn [acc k v]
                                (cond
                                  (#{:keys :syms :strs} k) (into acc (map #(symbol (name %))) v)
                                  (and (keyword? k) (= "keys" (name k))) (into acc (map #(symbol (name %))) v)
                                  (= :as k) (conj acc v)
                                  (= :or k) acc
                                  :else (into acc (pattern-syms k))))
                              #{} pattern)
    (coll? pattern) (reduce #(into %1 (pattern-syms %2)) #{} pattern)
    :else #{}))

(defn wgsl-name
  "A WGSL-safe identifier for a Clojure symbol."
  [sym]
  (let [s (-> (name sym)
              (str/replace "-" "_")
              (str/replace "?" "_q")
              (str/replace "!" "_b")
              (str/replace #"[^A-Za-z0-9_]" ""))]
    (if (re-find #"^[A-Za-z]" s) s (str "v" s))))

(declare compile-form)

(defn- compile-body [forms locals]
  (map #(compile-form % locals) forms))

(defn- thunk [forms locals]
  `(~'fn [& ~'_] ~@(compile-body forms locals)))

(defn contains-statement?
  "True when `form` uses a construct that needs a TSL Fn stack."
  [form]
  (cond
    (seq? form) (let [h (first form)]
                  (cond
                    (#{'clj 'quote} h) false
                    (contains? statement-heads h) true
                    :else (boolean (some contains-statement? form))))
    (coll? form) (boolean (some contains-statement? (if (map? form) (mapcat identity form) form)))
    :else false))

;; ---------------------------------------------------------------------------
;; Binding forms (CPU semantics, bodies compiled)
;; ---------------------------------------------------------------------------

(defn- compile-bindings
  "Compile the value side of a let-style binding vector, threading locals.
   Returns [compiled-bindings locals]."
  [bindings locals]
  (when-not (and (vector? bindings) (even? (count bindings)))
    (error "binding form must be a vector with an even number of forms" bindings))
  (reduce (fn [[out ls] [pattern value]]
            [(conj out pattern (compile-form value ls))
             (into ls (pattern-syms pattern))])
          [[] locals]
          (partition 2 bindings)))

(defn- compile-let [[op bindings & body] locals]
  (let [[bs ls] (compile-bindings bindings locals)]
    `(~op ~bs ~@(compile-body body ls))))

(defn- compile-fn-arity [[params & body] locals]
  (let [ls (into locals (pattern-syms params))]
    `(~params ~@(compile-body body ls))))

(defn- compile-fn [[op & more] locals]
  (let [[fname more] (if (symbol? (first more)) [(first more) (rest more)] [nil more])
        locals (cond-> locals fname (conj fname))
        arities (if (vector? (first more))
                  [(compile-fn-arity more locals)]
                  (map #(compile-fn-arity % locals) more))]
    (if (vector? (first more))
      `(~op ~@(when fname [fname]) ~@(first arities))
      `(~op ~@(when fname [fname]) ~@arities))))

(defn- compile-seq-bindings
  "for/doseq bindings: pairs plus :let/:when/:while modifiers."
  [bindings locals]
  (loop [pairs (partition 2 bindings) out [] ls locals]
    (if-let [[k v] (first pairs)]
      (cond
        (= :let k) (let [[bs ls'] (compile-bindings v ls)]
                     (recur (rest pairs) (conj out k bs) ls'))
        (keyword? k) (recur (rest pairs) (conj out k (compile-form v ls)) ls)
        :else (recur (rest pairs) (conj out k (compile-form v ls)) (into ls (pattern-syms k))))
      [out ls])))

(defn- compile-seq-form [[op bindings & body] locals]
  (let [[bs ls] (compile-seq-bindings bindings locals)]
    `(~op ~bs ~@(compile-body body ls))))

(defn- compile-letfn [[op fns & body] locals]
  (let [ls (into locals (map first fns))]
    `(~op ~(mapv (fn [[fname & arities]]
                   (second (compile-fn (list* 'fn fname arities) ls)))
                 fns)
      ~@(compile-body body ls))))

;; ---------------------------------------------------------------------------
;; Threading (expanded before compiling so argument positions stay correct)
;; ---------------------------------------------------------------------------

(defn- thread-step [x step last?]
  (cond
    (seq? step) (if last?
                  (concat step [x])
                  (list* (first step) x (rest step)))
    :else (list step x)))

(defn expand-threading [[op x & steps :as form]]
  (case op
    -> (reduce #(thread-step %1 %2 false) x steps)
    ->> (reduce #(thread-step %1 %2 true) x steps)
    as-> (let [[sym & steps] steps]
           `(~'let [~sym ~x ~@(interleave (repeat sym) (butlast steps))]
             ~(if (seq steps) (last steps) sym)))
    (cond-> cond->>) (let [g (gensym "cond")
                           last? (= 'cond->> op)]
                       `(~'let [~g ~x
                                ~@(mapcat (fn [[test step]]
                                            [g `(~'clj-if ~test ~(thread-step g step last?) ~g)])
                                          (partition 2 steps))]
                         ~g))
    (some-> some->>) (let [g (gensym "some")
                           last? (= 'some->> op)]
                       `(~'let [~g ~x
                                ~@(mapcat (fn [step]
                                            [g `(~'clj-if (nil? ~g) nil ~(thread-step g step last?))])
                                          steps)]
                         ~g))
    (error "unknown threading form" form)))

;; ---------------------------------------------------------------------------
;; Conditionals and statements
;; ---------------------------------------------------------------------------

(defn- compile-if [[op test then else :as form] locals]
  (when-not (<= 3 (count form) 4)
    (error "if takes a test, a then branch and an optional else branch" form))
  (let [test (compile-form test locals)
        test (if (= 'if-not op) `(~(rt-sym 'not) ~test) test)
        stmt? (or (contains-statement? then) (contains-statement? else))]
    `(~(rt-sym 'if*) ~test
      ~(thunk [then] locals)
      ~(when (= 4 (count form)) (thunk [else] locals))
      ~stmt?)))

(defn- compile-when [[op test & body] locals]
  (let [test (compile-form test locals)
        test (if (= 'when-not op) `(~(rt-sym 'not) ~test) test)]
    `(~(rt-sym 'if*) ~test ~(thunk body locals) nil true)))

(defn- compile-cond [[_ & clauses :as form] locals]
  (when (odd? (count clauses))
    (error "cond requires an even number of forms" form))
  (let [pairs (partition 2 clauses)
        stmt? (boolean (some (comp contains-statement? second) pairs))]
    `(~(rt-sym 'cond*) ~stmt?
      [~@(mapcat (fn [[test expr]]
                   [(if (keyword? test) test (thunk [test] locals))
                    (thunk [expr] locals)])
                 pairs)])))

(defn- compile-case [[op expr & clauses] locals]
  (let [default? (odd? (count clauses))
        pairs (partition 2 clauses)]
    `(~op ~(compile-form expr locals)
      ~@(mapcat (fn [[k v]] [k (compile-form v locals)]) pairs)
      ~@(when default? [(compile-form (last clauses) locals)]))))

(defn- compile-and-or [[op & args] locals]
  `(~(rt-sym (if (= 'and op) 'and* 'or*)) ~@(map #(thunk [%] locals) args)))

(defn- lvalue [target locals]
  (compile-form target locals))

(defn- compile-set! [[_ target value :as form] locals]
  (when-not (= 3 (count form)) (error "set! takes a target and a value" form))
  (let [h (head target)]
    (if (and (symbol? h)
             (str/starts-with? (name h) ".-")
             (not (swizzle? (subs (name h) 2))))
      ;; plain JS property: CPU set!
      `(set! (~h ~(compile-form (second target) locals)) ~(compile-form value locals))
      `(~(rt-sym 'assign!) ~(lvalue target locals) ~(compile-form value locals)))))

(defn- compile-op-assign [[op target value :as form] locals]
  (when-not (= 3 (count form)) (error (str op " takes a target and a value") form))
  `(~(rt-sym 'op-assign!) ~(get assign-ops op) ~(lvalue target locals) ~(compile-form value locals)))

(defn- compile-let-var [[_ bindings & body :as form] locals]
  (when-not (and (vector? bindings) (even? (count bindings)))
    (error "let-var takes a binding vector" form))
  (let [[bs ls] (reduce (fn [[out ls] [sym init]]
                          (when-not (plain-symbol? sym)
                            (error "let-var bindings must be symbols" form))
                          [(conj out sym `(~(rt-sym 'var!) ~(compile-form init ls)))
                           (conj ls sym)])
                        [[] locals]
                        (partition 2 bindings))]
    `(~'let ~bs ~@(compile-body body ls))))

(defn- loop-body [sym body locals]
  `(~'fn [~sym & ~'_] ~@(compile-body body (conj locals sym)) nil))

(defn- compile-dotimes [[_ [sym n :as bindings] & body :as form] locals]
  (when-not (and (vector? bindings) (= 2 (count bindings)) (plain-symbol? sym))
    (error "dotimes takes [sym count]" form))
  `(~(rt-sym 'loop-range) ~(wgsl-name sym) 0 ~(compile-form n locals) 1
    ~(loop-body sym body locals)))

(defn- compile-for-range [[_ [sym start end step :as bindings] & body :as form] locals]
  (when-not (and (vector? bindings) (<= 3 (count bindings) 4) (plain-symbol? sym))
    (error "for-range takes [sym start end] or [sym start end step]" form))
  `(~(rt-sym 'loop-range) ~(wgsl-name sym)
    ~(compile-form start locals) ~(compile-form end locals) ~(if step (compile-form step locals) 1)
    ~(loop-body sym body locals)))

(defn- compile-while [[_ test & body] locals]
  `(~(rt-sym 'loop-while) ~(compile-form test locals) (~'fn [& ~'_] ~@(compile-body body locals) nil)))

;; ---------------------------------------------------------------------------
;; Main dispatch
;; ---------------------------------------------------------------------------

(defn- compile-symbol [sym locals]
  (cond
    (contains? locals sym) sym
    (not (plain-symbol? sym)) sym
    (contains? operators sym) (rt-sym (get operators sym))
    (contains? tsl-names sym) `(~(rt-sym '$get) ~(get tsl-names sym))
    :else sym))

(defn- compile-vector [v locals]
  (case (count v)
    (2 3 4) `(~(rt-sym '$) ~(str "vec" (count v)) ~@(compile-body v locals))
    (error "vector literals in shader code become vec2/vec3/vec4 and need 2-4 elements; wrap CPU vectors in (clj ...)" v)))

(defn- compile-list [form locals]
  (let [h (first form)
        special? (and (symbol? h) (not (contains? locals h)))]
    (cond
      (and special? (#{'clj 'quote} h))
      (if (= 'clj h) (second form) form)

      (and special? (= 'clj-if h))
      `(if ~@(compile-body (rest form) locals))

      (and special? (#{'-> '->> 'as-> 'cond-> 'cond->> 'some-> 'some->>} h))
      (compile-form (expand-threading form) locals)

      (and special? (#{'let 'let* 'loop 'loop* 'binding 'with-redefs} h))
      (compile-let form locals)

      (and special? (#{'if-let 'when-let 'if-some 'when-some} h))
      (let [[op bindings & body] form
            [bs ls] (compile-bindings bindings locals)]
        `(~op ~bs ~@(compile-body body ls)))

      (and special? (#{'fn 'fn*} h)) (compile-fn form locals)
      (and special? (#{'for 'doseq} h)) (compile-seq-form form locals)
      (and special? (= 'letfn h)) (compile-letfn form locals)

      (and special? (#{'if 'if-not} h)) (compile-if form locals)
      (and special? (#{'when 'when-not} h)) (compile-when form locals)
      (and special? (= 'cond h)) (compile-cond form locals)
      (and special? (= 'case h)) (compile-case form locals)
      (and special? (#{'and 'or} h)) (compile-and-or form locals)

      (and special? (= 'set! h)) (compile-set! form locals)
      (and special? (contains? assign-ops h)) (compile-op-assign form locals)
      (and special? (= 'let-var h)) (compile-let-var form locals)
      (and special? (= 'dotimes h)) (compile-dotimes form locals)
      (and special? (= 'for-range h)) (compile-for-range form locals)
      (and special? (= 'while h)) (compile-while form locals)

      ;; (:xyz v) swizzles nodes, keeps map lookup for Clojure maps
      (and (keyword? h) (= 2 (count form)))
      `(~(rt-sym 'kw) ~(compile-form (second form) locals) ~h)

      ;; interop: keep the member, compile the target and args
      (and (symbol? h) (str/starts-with? (name h) "."))
      `(~h ~@(compile-body (rest form) locals))

      ;; (. target member args...)
      (and special? (= '. h))
      `(. ~(compile-form (second form) locals)
          ~@(let [[member & args] (drop 2 form)]
              (if (seq? member)
                [(list* (first member) (compile-body (rest member) locals))]
                (list* member (compile-body args locals)))))

      (and special? (= 'new h))
      `(new ~(second form) ~@(compile-body (drop 2 form) locals))

      (and special? (plain-symbol? h) (contains? operators h))
      `(~(rt-sym (get operators h)) ~@(compile-body (rest form) locals))

      (and special? (plain-symbol? h) (contains? tsl-names h))
      `(~(rt-sym '$) ~(get tsl-names h) ~@(compile-body (rest form) locals))

      (and special? (#{'def 'var} h)) form

      :else (map #(compile-form % locals) form))))

(defn compile-form
  "Compile one form of shader code. `locals` is a set of symbols bound in the
   surrounding scope; they shadow TSL names."
  [form locals]
  (cond
    (symbol? form) (compile-symbol form locals)
    (seq? form) (if (empty? form) form (compile-list form locals))
    (vector? form) (compile-vector form locals)
    (map? form) (with-meta (into {} (map (fn [[k v]] [(compile-form k locals) (compile-form v locals)])) form)
                  (meta form))
    (set? form) (into #{} (map #(compile-form % locals)) form)
    :else form))

;; ---------------------------------------------------------------------------
;; Entry points used by the macros
;; ---------------------------------------------------------------------------

(defn env-locals
  "Local symbols from a macro `&env`: ClojureScript analyzer envs keep them
   under :locals; SCI and Clojure pass a map keyed by the locals."
  [env]
  (set (filter symbol? (keys (if (contains? env :locals) (:locals env) env)))))

(defn- tail-map? [form]
  (cond
    (map? form) true
    (and (seq? form) (#{'let 'let-var 'do} (first form))) (tail-map? (last form))
    :else false))

(defn compile-shader
  "Compile the body of a `shader` form. Bodies that use statements are wrapped
   in a TSL Fn so If/Loop/assign have a stack to land on."
  [body locals]
  (let [compiled (compile-body body locals)]
    (if (contains-statement? body)
      (do
        (when (tail-map? (last body))
          (error "a shader that returns a map of outputs cannot use statements (set!, dotimes, when, ...) at the top level; move them into a defshader-fn"
                 (last body)))
        `(~(rt-sym 'call-fn) (~(rt-sym 'fn-node) (~'fn [& ~'_] ~@compiled) nil)))
      (if (= 1 (count compiled)) (first compiled) `(do ~@compiled)))))

(defn parse-params
  "[a :float b :vec3] -> {:syms [a b] :types [\"float\" \"vec3\"]}
   [a b]             -> {:syms [a b] :types nil}"
  [params form]
  (when-not (vector? params) (error "shader-fn parameters must be a vector" form))
  (let [typed? (some keyword? params)]
    (if typed?
      (do
        (when-not (and (even? (count params))
                       (every? plain-symbol? (take-nth 2 params))
                       (every? keyword? (take-nth 2 (rest params))))
          (error "typed shader-fn params must alternate symbol and type: [a :float b :vec3]" form))
        {:syms (vec (take-nth 2 params))
         :types (mapv name (take-nth 2 (rest params)))})
      (do
        (when-not (every? plain-symbol? params)
          (error "shader-fn params must be symbols (destructuring is not supported)" form))
        {:syms params :types nil}))))

(defn compile-shader-fn
  "(shader-fn name? [params] return-type? & body) -> code creating a TSL Fn
   wrapped in a Clojure function."
  [fname [params & more] locals form]
  (let [{:keys [syms types]} (parse-params params form)
        [ret body] (if (keyword? (first more)) [(name (first more)) (rest more)] [nil more])
        _ (when (and types (nil? ret))
            (error "typed shader-fn needs a return type after the params, e.g. [a :float] :vec3" form))
        _ (when (and ret (nil? types) (seq syms))
            (error "a return type needs typed params, e.g. [a :float] :vec3" form))
        _ (when (empty? body) (error "shader-fn needs a body" form))
        inputs (gensym "inputs")
        ls (into locals syms)
        void? (= "void" ret)
        layout (when ret
                 `(~(rt-sym 'layout) ~(wgsl-name (or fname (gensym "fn"))) ~ret
                   [~@(map (fn [s t] [(wgsl-name s) t]) syms (or types []))]))]
    `(~(rt-sym 'shader-fn*)
      (~'fn [~inputs & ~'_]
        (~'let [~@(mapcat (fn [s i] [s `(~(rt-sym 'fn-arg) ~inputs ~i ~(wgsl-name s))]) syms (range))]
          ~@(compile-body body ls)
          ~@(when void? [nil])))
      ~layout
      ~(count syms))))

(defn compile-compute
  "(compute {:count n :workgroup-size [64]} & body)"
  [[opts & body] locals form]
  (when-not (map? opts) (error "compute takes an options map: {:count n :workgroup-size [64]}" form))
  (when-not (contains? opts :count) (error "compute options need :count" form))
  `(~(rt-sym 'compute*)
    (~'fn [& ~'_] ~@(compile-body body locals) nil)
    ~(compile-form (:count opts) locals)
    ~(:workgroup-size opts)))

;; ---------------------------------------------------------------------------
;; Macro expansions, shared by the shadow-cljs macros and the SCI macros
;; ---------------------------------------------------------------------------

(defn- split-doc [args]
  (if (string? (first args)) [(first args) (rest args)] [nil args]))

(defn expand-shader [body env]
  (compile-shader body (env-locals env)))

(defn expand-defshader [[fname & fdecl]]
  (let [[doc [params & body]] (split-doc fdecl)]
    `(~'defn ~fname ~@(when doc [doc]) ~params
      (~(rt-sym 'shader) ~@body))))

(defn expand-shader-fn [args env form]
  (let [[fname args] (if (symbol? (first args)) [(first args) (rest args)] [nil args])]
    (compile-shader-fn fname args (env-locals env) form)))

(defn expand-defshader-fn [[fname & args] env form]
  (let [[doc args] (split-doc args)]
    `(~'def ~fname ~@(when doc [doc])
      ~(compile-shader-fn fname args (env-locals env) form))))

(defn expand-compute [args env form]
  (compile-compute args (env-locals env) form))

(defn expand-defcompute [[cname & args] env form]
  (let [[doc args] (split-doc args)]
    `(~'def ~cname ~@(when doc [doc])
      ~(compile-compute args (env-locals env) form))))
