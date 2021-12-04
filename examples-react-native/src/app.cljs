(ns app
  (:require [reagent.core :as r]
            [threeagent.core :as th]
            ["expo-status-bar" :refer [StatusBar]]
            ["react-native" :as rn]
            ["expo-gl" :as expo-gl]
            ["expo-three" :as expo-three]
            [expo-root :as expo-root]))

(defonce state (th/atom))

(defn- tick! [delta-time]
  (swap! state update :time + delta-time))

(defn- post-render! [gl]
  (.endFrameEXP gl))

(defn- spinning-box []
  (let [r @(th/cursor state [:time])]
    [:box {:rotation [0 r 0]
           :material {:color "blue"}}]))
(defn- root-3d []
  [:object
   [:ambient-light {:intensity 0.4}]
   [:directional-light {:intensity 0.5}]
   [:object {:position [0 0 -4]}
    [spinning-box]]])

(defn- start-threeagent [^js gl]
  (js/console.log "HERE" gl)
  (let [width (.-drawingBufferWidth gl)
        height (.-drawingBufferHeight gl)
        _ (set! (.-canvas gl)
                #js {:width width
                     :height height})
        renderer (expo-three/Renderer. #js {:gl gl})]
    (.setSize renderer width height)
    (js/console.log (.getPixelRatio renderer))
    (th/render-react-native root-3d renderer {:on-before-render tick!
                                              :on-after-render #(post-render! gl)})))

(defn root []
   [:> expo-gl/GLView {:style {:flex 1
                               :background-color :white}
                       :on-context-create (fn [gl]
                                            (start-threeagent gl))}])

(defn start
  {:dev/after-load true}
  []
  (expo-root/render-root (r/as-element [root])))

(defn init []
  (start))


