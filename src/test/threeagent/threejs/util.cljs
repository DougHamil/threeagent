(ns threeagent.threejs.util)

(defonce canvases (atom {}))

(defn create-canvas [key]
  (if-let [c (get @canvases key)]
    c
    (let [c (.createElement js/document "canvas")]
      (.appendChild js/document.body c)
      (swap! canvases assoc key c)
      c)))

(defn destroy-canvas! [key]
  (when-let [c (get @canvases key)]
    (.removeChild js/document.body c)
    (swap! canvases dissoc key)))

(defn get-in-scene [obj path]
  (if (seq path)
    (recur (aget (.-children obj) (first path))
           (rest path))
    obj))

(defn child-count [obj]
  (.-length (.-children obj)))

(defn get-in-object [parent path]
  (if (seq path)
    (let [next (first path)]
      (if (string? next)
        (recur (.getObjectByName parent next) (rest path))
        (recur (aget (.-children parent) next) (rest path))))
    parent))
