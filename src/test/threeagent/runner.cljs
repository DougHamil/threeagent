(ns threeagent.runner
  (:require [jx.reporter.karma :refer-macros [run-all-tests]]
            [cljs.test :as test]
            [threeagent.ratom-test]
            [threeagent.component-test]
            [threeagent.virtual-scene-test]))

(enable-console-print!)

(defn ^:export run-all [karma]
  (run-all-tests karma))
