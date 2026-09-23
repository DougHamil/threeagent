(ns threeagent.tsl.sci
  "The `threeagent.tsl` macros as SCI macros, for evaluating shader code in a
   SCI context (e.g. live-edited docs). No dependency on SCI itself:

     (sci/init {:namespaces {'threeagent.tsl
                             (merge (sci/copy-ns threeagent.tsl (sci/create-ns 'threeagent.tsl))
                                    threeagent.tsl.sci/macros)}})"
  (:require [threeagent.tsl.compiler :as c]))

(defn- macro [f]
  (vary-meta f assoc :sci/macro true))

(def macros
  {'shader       (macro (fn [_form env & body] (c/expand-shader body env)))
   'defshader    (macro (fn [_form _env & args] (c/expand-defshader args)))
   'shader-fn    (macro (fn [form env & args] (c/expand-shader-fn args env form)))
   'defshader-fn (macro (fn [form env & args] (c/expand-defshader-fn args env form)))
   'compute      (macro (fn [form env & args] (c/expand-compute args env form)))
   'defcompute   (macro (fn [form env & args] (c/expand-defcompute args env form)))})
