(ns threeagent.runner
  (:require [jx.reporter.karma :refer-macros [run-all-tests]]
            [threeagent.ratom-test]
            [threeagent.virtual-scene.test]
            [threeagent.e2e.test]
            [threeagent.tsl.compiler-test]
            [threeagent.tsl.runtime-test]
            [threeagent.tsl.material-test]
            [threeagent.tsl.sci-test]
            [threeagent.tsl.pipeline-test]))

(enable-console-print!)

(defn ^:export run-all [karma]
  (run-all-tests karma))
