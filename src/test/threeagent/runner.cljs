(ns threeagent.runner
  (:require [jx.reporter.karma :refer-macros [run-all-tests]]
            [threeagent.ratom-test]
            [threeagent.virtual-scene.test]
            [threeagent.e2e.test]))

(enable-console-print!)

(defn ^:export run-all [karma]
  (run-all-tests karma))
