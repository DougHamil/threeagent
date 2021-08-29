(ns threeagent.runner
  (:require [jx.reporter.karma :refer-macros [run-all-tests]]
            [cljs.test :as test]
            [threeagent.ratom-test]
            [threeagent.component-test]
            [threeagent.virtual-scene-test]
            [threeagent.edge-case-test]
            [threeagent.parent-transform-update-test]
            [threeagent.delete-child-with-key-test]
            [threeagent.metadata-on-fn-component-test]
            [threeagent.type-two-component-reaction-test]
            [threeagent.threejs.nested-objects-test]
            [threeagent.system-test]))

(enable-console-print!)

(defn ^:export run-all [karma]
  (run-all-tests karma))
