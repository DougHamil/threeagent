(ns threeagent.component)

(defonce ^:dynamic *registry* {})

(defn register-component-renderer! [key render-fn]
  (set! *registry* (assoc *registry* key render-fn)))
