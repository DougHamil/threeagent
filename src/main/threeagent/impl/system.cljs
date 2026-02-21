(ns threeagent.impl.system
  (:require [threeagent.impl.types :refer [Context]]
            [threeagent.system :as system-protocol]))

(defn normalize-systems
  "Normalize the raw :systems map into [systems-map sorted-keys-vec].
   Values can be bare ISystem instances (default :order 0) or
   {:system <ISystem> :order <number>} config maps."
  [raw-systems]
  (let [entries (reduce-kv (fn [acc k v]
                             (if (and (map? v) (contains? v :system))
                               (conj acc {:key k :system (:system v) :order (or (:order v) 0)})
                               (conj acc {:key k :system v :order 0})))
                           []
                           raw-systems)
        sorted (sort-by :order entries)
        systems-map (reduce (fn [m {:keys [key system]}] (assoc m key system)) {} entries)
        sorted-keys (mapv :key sorted)]
    [systems-map sorted-keys]))

(defn dispatch-on-added [^Context context entity-context entity-id ^js entity-obj entity-config]
  (let [systems (.-systems context)
        system-keys (.-systemKeys context)
        callbacks (array)]
    (doseq [k system-keys]
      (when-let [v (get entity-config k)]
        (when-let [sys (get systems k)]
          (let [cb (system-protocol/on-entity-added sys entity-context entity-id entity-obj v)]
            (when (fn? cb)
              (.push callbacks cb))))))
    callbacks))

(defn dispatch-on-removed [^Context context entity-context entity-id ^js entity-obj entity-config]
  (let [systems (.-systems context)
        system-keys (.-systemKeys context)
        callbacks (array)]
    (doseq [k system-keys]
      (when-let [v (get entity-config k)]
        (when-let [sys (get systems k)]
          (let [cb (system-protocol/on-entity-removed sys entity-context entity-id entity-obj v)]
            (when (fn? cb)
              (.push callbacks cb))))))
    callbacks))

(defn dispatch-on-updated [^Context context entity-context entity-id ^js entity-obj entity-config]
  (let [systems (.-systems context)
        system-keys (.-systemKeys context)]
    (doseq [k system-keys]
      (when-let [v (get entity-config k)]
        (when-let [sys (get systems k)]
          (system-protocol/on-entity-updated sys entity-context entity-id entity-obj v))))))

(defn dispatch-on-tick [^Context context delta-time]
  (let [systems (.-systems context)
        system-keys (.-systemKeys context)]
    (doseq [k system-keys]
      (system-protocol/tick (get systems k) delta-time))))

(defn dispatch-init [systems sorted-keys context]
  (let [context (assoc context :systems systems)
        callbacks (array)]
    (doseq [k sorted-keys]
      (let [cb (system-protocol/init (get systems k) context)]
        (when (fn? cb)
          (.push callbacks cb))))
    (doseq [cb callbacks]
      (cb))))

(defn dispatch-destroy [systems sorted-keys context]
  (let [context (assoc context :systems systems)
        callbacks (array)]
    (doseq [k sorted-keys]
      (let [cb (system-protocol/destroy (get systems k) context)]
        (when (fn? cb)
          (.push callbacks cb))))
    (doseq [cb callbacks]
      (cb))))
