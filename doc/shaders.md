# Shaders (TSL)

Threeagent lets us write Three.js [TSL](https://threejs.org/docs/#api/en/nodes/TSL) shaders as ordinary Clojure. The `threeagent.tsl` macros rewrite Clojure forms into TSL node-graph code, node materials are plain data in `:material`, and RenderPipeline post-processing can be written as hiccup.

```clojure
(ns my.app
  (:require [threeagent.core :as th]
            [threeagent.tsl :refer [defshader defshader-fn]]))

(defshader-fn ring [d :float width :float] :float
  (smoothstep width 0 (abs (- d 0.4))))

(defshader telegraph [{:keys [time tint]}]
  (let [c     (- (uv) [0.5 0.5])
        d     (length c)
        angle (/ (atan (:y c) (:x c)) two-pi)]
    {:color   (* tint (fract (+ angle (* time 0.3))))
     :opacity (* (ring d 0.05) 0.7)}))

(defn root []
  [:plane {:material {:shader      telegraph
                      :transparent true
                      :blending    :additive
                      :uniforms    {:time @game-time
                                    :tint [0.9 0.15 0.1]}}}])
```

## Shader code

Inside `shader`, `defshader`, `shader-fn`, `defshader-fn`, `compute` and `defcompute`:

| Clojure | TSL |
| --- | --- |
| `(+ a b c)` `(- a)` `(* a b)` `(/ a b)` `(mod a b)` | `add` `negate` `mul` `div` `mod` |
| `(< a b)` `(= a b)` `(not= a b)` `(and a b)` `(or a b)` `(not a)` | `lessThan` `equal` `notEqual` `and` `or` `not` |
| `[x y]` `[x y z]` `[x y z w]` | `vec2` `vec3` `vec4` |
| `(:xyz v)` `(.-x v)` | swizzles |
| `(nth buf i)` | `buf.element(i)` |
| `(if test a b)` | `select(test, a, b)` |
| `(when test ...)` `(cond ...)` with statements | `If(...).ElseIf(...).Else(...)` |
| `(let-var [acc 0] ...)` | `Var(0)`, a mutable variable |
| `(set! x v)` `(+= x v)` `(-= x v)` `(*= x v)` `(%= x v)` | `assign` `addAssign` ... |
| `(dotimes [i n] ...)` `(for-range [i start end step] ...)` `(while test ...)` | `Loop` |
| `(break)` `(continue)` `(discard)` `(return)` | `Break` `Continue` `Discard` `Return` |

Any other three/tsl export can be used by its JS name or its kebab-case name: `position-local`, `normal-world`, `mx-noise-float`, `instance-index`, `two-pi`.

Operators, conditionals, `and`/`or` and `nth` **dispatch on their arguments**:

- If any argument is a node, they build nodes.
- Otherwise they keep their Clojure meaning.

So CPU-side logic mixes freely with shader code:

```clojure
(defshader surface [{:keys [fog?]}]
  (let [base (* (texture albedo (uv)) 1.2)]   ; node math
    (if fog?                                  ; plain boolean: a Clojure if
      (mix base fog-color (fog-factor))
      base)))
```

Symbol resolution follows these rules:

- **Local bindings win.** A `let`, `fn` or macro argument named `length` shadows TSL's `length`.
- **Other names:**
  - Qualified names (`my.ns/length`) and names TSL doesn't export pass through unchanged.
  - Vars from your own namespace pass through too, as long as they don't collide with a TSL name.
- **`range`, `hash`, `print` and `array` keep their Clojure meaning.** Reach the TSL versions with `(threeagent.tsl/$ "range" ...)`.
- **`(clj form)`** leaves `form` completely untouched.
- **Raw interop** such as `(.toVar n)` and `(.-value u)` always works, for anything the macros don't cover.

### Number literals

TSL gives a bare number the type of the other operand, so with an `int` node, JS TSL's `i.mul(0.25)` compiles to `i * 0`. Inside shader code, a fractional number meeting a node promotes the node to float (`int` → `float`, `ivec3` → `vec3`), so `(* i 0.25)` does what it says. Whole numbers are left alone: `(* i 2.0)` is still integer math when `i` is an `int` (2.0 and 2 are the same JS number), so write `(* (float i) 2)`.

### Statements need a stack

`set!`, `+=`, `let-var`, loops and node-conditioned `when`/`cond` only work inside a TSL `Fn`. `shader` and `defshader` wrap their body in one automatically when it uses them. A shader that returns a *map* of outputs can't be wrapped, so move the statements into a `defshader-fn`:

```clojure
(defshader-fn falloff [d :float] :float
  (let-var [acc 0]
    (dotimes [i 4]
      (when (> d (* i 0.25))
        (+= acc 0.25)))
    (- 1 acc)))
```

## Functions

```clojure
(defshader-fn scale [v :vec3 k :float] :vec3 (* v k)) ; a named WGSL/GLSL function
(defshader-fn blend [a b] (mix a b 0.5))               ; untyped: inlined at each call
(defshader-fn splat! [p :vec3] :void (set! ...))       ; statement function

(scale position-local 2)                               ; call like any Clojure fn
```

They are ordinary Clojure functions wrapping a TSL `Fn`, so calling them never runs into the Proxy/IFn problem that release builds have with raw `tsl/Fn` results. `(threeagent.tsl/fn-node-of scale)` returns the underlying `Fn`.

## Compute

```clojure
(defcompute integrate {:count particle-count :workgroup-size [64]}
  (let [i instance-index]
    (set! (nth positions i) (+ (nth positions i) (* (nth velocities i) dt)))))

(.compute renderer integrate)
```

## Node materials

A `:material` map with a `:shader` key is a node material:

| Key | |
| --- | --- |
| `:shader` | `(fn [uniforms] node-or-outputs)`, usually a `defshader` |
| `:type` | `:basic` (default) `:standard` `:physical` `:phong` `:lambert` `:toon` `:matcap` `:normal` `:sss` `:sprite` `:points` `:line` `:line-dashed` `:line2` `:volume` `:shadow` `:node`, or a material class |
| `:uniforms` | `{:name value}`. Numbers, booleans, `[x y]`/`[x y z]`/`[x y z w]`, textures, Three.js objects |
| anything else | material properties, kebab-case: `:transparent`, `:depth-write`, `:blending :additive`, `:side :double`, ... |

The shader returns a node, which becomes the `colorNode`, or a map of outputs where each key `k` sets `<k>Node`: `:color`, `:opacity`, `:position`, `:normal`, `:emissive`, `:roughness`, `:fragment`, `:output`, ...

**Reactivity.** When only uniform values change between renders, threeagent writes `uniform.value` and never recompiles. Deref a `th/atom` in `:uniforms` and the shader updates the next frame. Changing the shader fn, `:type`, a property, or the set or kind of uniforms builds a new material.

**Sharing.** Materials without `:uniforms` are shared by every mesh that uses the same spec. Materials with `:uniforms` belong to their mesh. For values that change every frame on many meshes (like time), prefer one shared `(tsl/uniform 0)` referenced from the shader, updated imperatively, over per-mesh `:uniforms`.

**Hot reload.** Redefining a `defshader` gives it a new identity, so the next render builds new materials from it. Materials nothing uses anymore are disposed when the scene resets.

`threeagent.tsl.material` also exposes `acquire!`, `update!`, `release!`, `material-of` and `uniforms-of` for custom entity types.

## Post-processing as hiccup

`:render-pipeline` accepts hiccup as well as a function:

```clojure
(require '[threeagent.tsl.pipeline :as pipeline]
         '["three/addons/tsl/display/BloomNode.js" :refer [bloom]])

(pipeline/register-effect! :bloom (fn [[input] {:keys [strength radius threshold]}]
                                    (bloom input strength radius threshold)))

(th/render {:world world :ui ui} root-el
           {:render-order [:world :ui]
            :render-pipeline [:add [:add :world [:bloom {:strength 0.6} :world]] :ui]})
```

- **A scene keyword** (`:world`, or `:default` for a single scene) is that scene's color output. `[:pass :world "depth"]` picks another output.
- **`[effect opts? & inputs]`** calls `(effect input-nodes opts)`. The head is a function, a keyword from `register-effect!`, or one of the built-ins `:add` `:sub` `:mul` `:mix`.

## SCI

The macros are built on a portable compiler, so shader code also runs in [SCI](https://github.com/babashka/sci) (e.g. live-edited docs):

```clojure
(sci/init {:namespaces {'threeagent.tsl
                        (merge (sci/copy-ns threeagent.tsl (sci/create-ns 'threeagent.tsl))
                               threeagent.tsl.sci/macros)}})
```

## clj-kondo

The jar exports a clj-kondo config (`clj-kondo.exports/doughamil/threeagent`). It teaches kondo the macros and silences unresolved-symbol and type warnings inside shader code. Import it with `clj-kondo --copy-configs --dependencies --lint "$(classpath)"`.

## Upgrading Three.js

The list of names the compiler treats as TSL is generated from the installed three:

```bash
node scripts/gen-tsl-exports.mjs
```
