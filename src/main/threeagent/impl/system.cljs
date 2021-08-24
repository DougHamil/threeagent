(ns threeagent.impl.system
  (:require [threeagent.impl.types :refer [Context]]
            [threeagent.system :as system-protocol]))

(defn dispatch-on-added [^Context context key ^js obj node-config]
 (let [systems (.-systems context)]
   (doseq [[k v] node-config]
     (when-let [sys (get systems k)]
       (system-protocol/on-entity-added sys key obj v)))))

(defn dispatch-on-removed [^Context context key ^js obj node-config]
  (let [systems (.-systems context)]
    (doseq [[k v] node-config]
      (when-let [sys (get systems k)]
        (system-protocol/on-entity-removed sys key obj v)))))

(defn dispatch-on-tick [^Context context delta-time]
  (doseq [[_ system] (.-systems context)]
    (system-protocol/tick system delta-time)))
  
(defn dispatch-init [systems context]
  (doseq [[_ system] systems]
    (system-protocol/init system context)))

(defn dispatch-destroy [systems context]
  (doseq [[_ system] systems]
    (system-protocol/destroy system context)))
