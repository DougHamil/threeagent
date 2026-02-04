(ns threeagent.impl.registry
  (:require ["three/webgpu" :as three]))

(defn reset-registry! [registry]
  (when registry
    (reset! registry {})))

(defn register-entity! [registry id ^three/Object3D obj]
  (when (and registry id)
    (swap! registry assoc id obj)))

(defn unregister-entity! [registry id]
  (when (and registry id)
    (swap! registry dissoc id)))

