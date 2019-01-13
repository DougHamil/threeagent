(ns threeagent.core-test
  (:require [reagent.core :as r]))

(defn start [])
(defn stop [done]
  (done))

(defn ^:export init []
  (println "Test"))
