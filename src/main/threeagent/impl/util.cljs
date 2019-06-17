(ns threeagent.impl.util)

(defn log [m] (.log js/console m))

(defonce pi-times-2 (* js/Math.PI 2.0))
(defonce pi js/Math.PI)
(defonce pi-over-2 (/ js/Math.PI 2.0))
