(ns hooks.threeagent.tsl
  "shader-fn / defshader-fn params may carry type keywords ([v :vec3 k :float] :vec3);
   rewrite them to plain fns so bindings are analyzed."
  (:require [clj-kondo.hooks-api :as api]))

(defn- fn-parts [args]
  (let [[fname args] (if (symbol? (api/sexpr (first args)))
                       [(first args) (rest args)]
                       [nil args])
        [params & more] args
        body (if (and (seq more) (api/keyword-node? (first more))) (rest more) more)]
    {:fname fname
     :params (api/vector-node (remove api/keyword-node? (:children params)))
     :body body}))

(defn shader-fn [{:keys [node]}]
  (let [{:keys [fname params body]} (fn-parts (rest (:children node)))]
    {:node (api/list-node (concat [(api/token-node 'fn)]
                                  (when fname [fname])
                                  [params]
                                  body))}))

(defn defshader-fn [{:keys [node]}]
  (let [[_ fname & args] (:children node)
        args (if (api/string-node? (first args)) (rest args) args)
        {:keys [params body]} (fn-parts args)]
    {:node (api/list-node [(api/token-node 'def)
                           fname
                           (api/list-node (concat [(api/token-node 'fn) params] body))])}))

(defn defcompute
  "(defcompute name doc? opts & body) -> (def name (do opts body...))"
  [{:keys [node]}]
  (let [[_ cname & args] (:children node)
        args (if (api/string-node? (first args)) (rest args) args)]
    {:node (api/list-node [(api/token-node 'def)
                           cname
                           (api/list-node (cons (api/token-node 'do) args))])}))
