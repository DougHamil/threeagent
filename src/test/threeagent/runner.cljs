(ns threeagent.runner
  (:require [jx.reporter.karma :refer-macros [run-all-tests]]
            [cljs.test :as test]
            [threeagent.ratom-test]
            [threeagent.component-test]
            [threeagent.virtual-scene-test]
            [threeagent.edge-case-test]
            [threeagent.parent-transform-update-test]))

(enable-console-print!)

(defn ^:export run-all [karma]
  (run-all-tests karma))
