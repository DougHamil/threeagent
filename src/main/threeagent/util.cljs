(ns threeagent.util)

(defn $ [o k] (when o (aget o k)))
(defn $! [o k v] (when o (aset o k v)))
(defn log [m] (.log js/console m))
