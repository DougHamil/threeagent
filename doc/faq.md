# FAQ

## How do I embed Threeagent in a Reagent application?

One solution is to define a form-3 Reagent component that creates the `<canvas>` DOM element and
calls `threeagent.core/render` to kick-off Threeagent:

```clojure
(ns app
  (:require [threeagent.core :as th]
            [reagent.dom :as rdom]
            [reagent.core :as r]))

(defn- threeagent-root []
  [:object
   [:ambient-light {:intensity 1.0}]
   [:box {:position [0 0 -5]}]])

(defn- threeagent-scene [root-fn]
  (r/create-class
   {:display-name "threeagent"
    :reagent-render (fn [] [:canvas])
    :component-did-mount
    (fn [this]
      (th/render root-fn (rdom/dom-node this)))
    :component-did-update
    (fn [this]
      (th/render root-fn (rdom/dom-node this)))}))

(defn root []
  [:div
   [:h1 "Embedded Threeagent Scene"]
   [threeagent-scene threeagent-root]])

(defn ^:dev/after-load init []
  (rdom/render [root] (.getElementById js/document "root")))
```

