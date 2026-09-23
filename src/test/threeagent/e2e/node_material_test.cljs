(ns threeagent.e2e.node-material-test
  (:require [cljs.test :refer-macros [deftest is use-fixtures async]]
            [threeagent.e2e.fixture :as fixture]
            [threeagent.core :as th]
            [threeagent.tsl :refer-macros [defshader defshader-fn]]
            [threeagent.tsl.material :as m]))

(defonce canvas (atom nil))

(use-fixtures :each (fixture/with-canvas canvas))

(defshader-fn falloff [d :float] :float
  (let-var [acc 0]
    (dotimes [i 4]
      (when (> d (* i 0.25))
        (+= acc 0.25)))
    (- 1 acc)))

(defshader pulse [{:keys [strength tint]}]
  {:color (* tint strength (falloff (length (- (uv) [0.5 0.5]))))
   :opacity (smoothstep 0.5 0.4 (length (- (uv) [0.5 0.5])))})

(defn- capture-errors []
  (let [errors (atom [])
        original js/console.error]
    (set! js/console.error (fn [& args]
                             (swap! errors conj (apply str args))
                             (.apply original js/console (to-array args))))
    {:errors errors
     :restore #(set! js/console.error original)}))

(deftest node-material-mesh
  (let [strength (th/atom 1)
        mesh (atom nil)
        {:keys [errors restore]} (capture-errors)
        root-fn (fn []
                  [:box {:on-added #(reset! mesh %)
                         :material {:shader pulse
                                    :transparent true
                                    :uniforms {:strength @strength
                                               :tint [1 0.5 0.25]}}}])]
    (fixture/async-run!
     [{:when #(th/render root-fn @canvas)
       :then (fn []
               (is (some? @mesh))
               (is (= 1 (.-value ^js (:strength (m/uniforms-of @mesh))))))}
      {:when #(reset! strength 5)
       :then (fn []
               (let [^js mat (.-material ^js @mesh)]
                 (is (identical? mat (m/material-of @mesh)))
                 (is (= 5 (.-value ^js (:strength (m/uniforms-of @mesh))))))
               (restore)
               (is (empty? (filter #(re-find #"TSL|WGSL|GLSL|shader" %) @errors))
                   (str "shader errors: " (pr-str @errors))))}])))

(deftest generated-shader-code
  (let [mesh (atom nil)
        root-fn (fn []
                  [:box {:on-added #(reset! mesh %)
                         :material {:shader pulse
                                    :uniforms {:strength 1 :tint [1 1 1]}}}])
        ctx (th/render root-fn @canvas)
        ^js renderer (:threejs-renderer ctx)]
    (async done
           (-> (.init renderer)
               (.then #(.getShaderAsync (.-debug renderer)
                                        (:threejs-scene ctx)
                                        (:threejs-default-camera ctx)
                                        @mesh))
               (.then (fn [^js shaders]
                        (let [frag (.-fragmentShader shaders)]
                          (is (re-find #"falloff\s*\(" frag) "typed defshader-fn emits a named function")
                          (is (re-find #"for\s*\(" frag) "dotimes emits a loop")
                          (is (re-find #"if\s*\(" frag) "when with a node test emits an if")
                          (is (re-find #"if[^\n]*0\.25" frag)
                              "fractional literals against an int node stay fractional"))
                        (done)))
               (.catch (fn [e]
                         (is (nil? e) (str "shader generation failed: " e))
                         (done)))))))

(defn- after-frames [n f]
  (if (zero? n)
    (f)
    (js/requestAnimationFrame #(after-frames (dec n) f))))

(deftest hiccup-render-pipeline
  (let [ctx (th/render (fn [] [:box {}]) @canvas
                       {:render-pipeline [:mul :default 0.5]})
        ^js renderer (:threejs-renderer ctx)
        ^js raw (:threeagent/raw-context ctx)]
    (async done
           (-> (.init renderer)
               (.then (fn []
                        (after-frames 3
                                      (fn []
                                        (is (some? (.-renderPipeline raw)) "pipeline built from hiccup")
                                        (is (some? (.-outputNode ^js (.-renderPipeline raw))))
                                        (done)))))))))
