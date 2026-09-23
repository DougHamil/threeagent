(ns threeagent.tsl.pipeline
  "RenderPipeline output described as hiccup, for the `:render-pipeline` option
   of `threeagent.core/render`:

     [:add :world [bloom-effect {:strength 0.4} :world]]

   - a keyword naming a scene (`:world`, or `:default` for a single scene) is
     that scene pass's color output
   - `[:pass scene-key texture-name]` picks another pass output, e.g. \"depth\"
   - `[f opts? & inputs]` calls `(f [input-nodes...] opts)` - any function
     returning a node, typically a thin wrapper around a three addon
   - `[k opts? & inputs]` with a keyword looks `k` up in the effects registered
     through `register-effect!`, then in the built-ins :add :sub :mul :mix
   - anything else (nodes, numbers) is used as is"
  (:require [threeagent.tsl :as tsl]))

(defonce ^:private effects
  (atom {:add (fn [inputs _] (apply tsl/add inputs))
         :sub (fn [inputs _] (apply tsl/sub inputs))
         :mul (fn [inputs _] (apply tsl/mul inputs))
         :mix (fn [inputs _] (apply tsl/$ "mix" inputs))}))

(defn register-effect!
  "Make `(f input-nodes opts)` available as `[k opts? & inputs]`."
  [k f]
  (swap! effects assoc k f))

(defn- pass-output [passes k texture-name]
  (if-let [^js p (get passes k)]
    (.getTextureNode p texture-name)
    (throw (js/Error. (str "Render pipeline references unknown scene " k
                           ", known scenes: " (keys passes))))))

(defn build
  "Turn a pipeline hiccup `form` into an output node given `{scene-key -> pass}`."
  [form passes]
  (cond
    (keyword? form) (pass-output passes form "output")

    (vector? form)
    (let [[head & more] form]
      (if (= :pass head)
        (let [[k texture-name] more]
          (pass-output passes k (or texture-name "output")))
        (let [[opts inputs] (if (map? (first more)) [(first more) (rest more)] [{} more])
              f (cond
                  (fn? head) head
                  (keyword? head) (or (get @effects head)
                                      (throw (js/Error. (str "Unknown render pipeline effect " head))))
                  :else (throw (js/Error. (str "Invalid render pipeline effect " head))))]
          (f (mapv #(build % passes) inputs) opts))))

    :else form))

(defn ->pipeline-fn
  "The `:render-pipeline` option as a fn of passes: hiccup is compiled with
   `build`, fns are returned as is."
  [render-pipeline]
  (if (vector? render-pipeline)
    (fn [passes] (build render-pipeline passes))
    render-pipeline))
