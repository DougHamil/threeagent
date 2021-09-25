(ns threeagent.impl.system
  (:require [threeagent.impl.types :refer [Context]]
            [threeagent.system :as system-protocol]))

(defn dispatch-on-added [^Context context key ^js obj node-config]
  (let [systems (.-systems context)
        callbacks (array)]
    (doseq [[k v] node-config]
      (when-let [sys (get systems k)]
        (let [cb (system-protocol/on-entity-added sys key obj v)]
          (when (fn? cb)
            (.push callbacks cb)))))
    callbacks))

(defn dispatch-on-removed [^Context context key ^js obj node-config]
  (let [systems (.-systems context)
        callbacks (array)]
    (doseq [[k v] node-config]
      (when-let [sys (get systems k)]
        (let [cb (system-protocol/on-entity-removed sys key obj v)]
          (when (fn? cb)
            (.push callbacks cb)))))
    callbacks))

(defn dispatch-on-tick [^Context context delta-time]
  (doseq [[_ system] (.-systems context)]
    (system-protocol/tick system delta-time)))
  
(defn dispatch-init [systems context]
  (let [context (assoc context :systems systems)
        callbacks (array)]
    (doseq [[_ system] systems]
      (let [cb (system-protocol/init system context)]
        (when (fn? cb)
          (.push callbacks cb))))
    (doseq [cb callbacks]
      (cb))))

(defn dispatch-destroy [systems context]
  (let [context (assoc context :systems systems)
        callbacks (array)]
    (doseq [[_ system] systems]
      (let [cb (system-protocol/destroy system context)]
        (when (fn? cb)
          (.push callbacks cb))))
    (doseq [cb callbacks]
      (cb))))
