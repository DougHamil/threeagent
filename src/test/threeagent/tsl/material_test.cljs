(ns threeagent.tsl.material-test
  (:require [cljs.test :refer-macros [deftest is testing]]
            ["three/webgpu" :as three]
            [threeagent.tsl :refer-macros [defshader]]
            [threeagent.tsl.material :as m]))

(defshader glow [{:keys [strength]}]
  {:color (* [1 0.5 0] (or strength 1))
   :opacity 0.5})

(defshader flat [_]
  [0 1 0])

(deftest spec-detection
  (is (m/spec? {:shader glow}))
  (is (not (m/spec? {:color 0xff0000}))))

(deftest outputs-and-props
  (let [owner #js {}
        ^js mat (m/acquire! owner {:shader glow :transparent true :blending :additive :side :double})]
    (is (instance? three/MeshBasicNodeMaterial mat))
    (is (some? (.-colorNode mat)))
    (is (= 0.5 (.-opacityNode mat)))
    (is (true? (.-transparent mat)))
    (is (= three/AdditiveBlending (.-blending mat)))
    (is (= three/DoubleSide (.-side mat)))
    (m/release! owner)))

(deftest node-result-is-color
  (let [owner #js {}
        ^js mat (m/acquire! owner {:shader flat :type :standard})]
    (is (instance? three/MeshStandardNodeMaterial mat))
    (is (some? (.-colorNode mat)))
    (m/release! owner)))

(deftest shared-materials
  (let [a #js {} b #js {}
        spec {:shader flat}
        mat-a (m/acquire! a spec)
        mat-b (m/acquire! b spec)]
    (is (identical? mat-a mat-b) "same spec, no uniforms -> shared")
    (m/release! a)
    (m/release! b)
    (let [disposed (atom false)]
      (.addEventListener ^js mat-a "dispose" #(reset! disposed true))
      (m/dispose-unused!)
      (is @disposed))))

(deftest uniform-updates
  (let [owner #js {}
        mat (m/acquire! owner {:shader glow :uniforms {:strength 1}})
        ^js u (:strength (m/uniforms-of owner))]
    (is (= 1 (.-value u)))
    (testing "value change writes the uniform, same material"
      (is (identical? mat (m/update! owner {:shader glow :uniforms {:strength 3}})))
      (is (= 3 (.-value u))))
    (testing "vector uniforms update in place"
      (let [mat2 (m/update! owner {:shader glow :uniforms {:strength [1 2 3]}})
            ^js v (.-value (:strength (m/uniforms-of owner)))]
        (is (not (identical? mat mat2)) "kind changed number -> vec3: rebuilt")
        (m/update! owner {:shader glow :uniforms {:strength [4 5 6]}})
        (is (= [4 5 6] [(.-x v) (.-y v) (.-z v)]))))
    (testing "new shader fn rebuilds (hot reload)"
      (let [before (m/material-of owner)]
        (is (not (identical? before (m/update! owner {:shader flat :uniforms {:strength [4 5 6]}}))))))
    (m/release! owner)
    (is (nil? (m/material-of owner)))))
