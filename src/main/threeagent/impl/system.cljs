(ns threeagent.impl.system
  (:require [threeagent.impl.types :refer [Context]]
            [threeagent.system :as system-protocol]))

(defn dispatch-on-added [^Context context entity-context entity-id ^js entity-obj entity-config]
  (let [systems (.-systems context)
        callbacks (array)]
    (doseq [[k v] entity-config]
      (when-let [sys (get systems k)]
        (let [cb (system-protocol/on-entity-added sys entity-context entity-id entity-obj v)]
          (when (fn? cb)
            (.push callbacks cb)))))
    callbacks))

(defn dispatch-on-removed [^Context context entity-context entity-id ^js entity-obj entity-config]
  (let [systems (.-systems context)
        callbacks (array)]
    (doseq [[k v] entity-config]
      (when-let [sys (get systems k)]
        (let [cb (system-protocol/on-entity-removed sys entity-context entity-id entity-obj v)]
          (when (fn? cb)
            (.push callbacks cb)))))
    callbacks))

(defn dispatch-on-updated [^Context context entity-context entity-id ^js entity-obj entity-config]
  (let [systems (.-systems context)]
    (doseq [[k v] entity-config]
      (when-let [sys (get systems k)]
        (system-protocol/on-entity-updated sys entity-context entity-id entity-obj v)))))

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
