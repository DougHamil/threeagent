(ns threeagent.runner
  (:require [doo.runner :refer-macros [doo-tests doo-all-tests]]
            [cljs.test :as test]
            [THREE]
            [threeagent.component-test]
            [threeagent.virtual-scene-test]))

(enable-console-print!)
(doo-all-tests #"threeagent\..*(?:-test)$")
