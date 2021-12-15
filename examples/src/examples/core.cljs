(ns examples.core
  (:require
   [reagent.dom :as rdom]
   [re-frame.core :as re-frame]
   [examples.retrying :as retrying]
   [examples.clearing :as clearing]
   [examples.input :as input]
   [examples.wizard :as wizard]
   [day8.re-frame.http-fx]
   ))

(defn main-panel []
  [:article.space-y-8.container.mx-auto.p-8
   [:section.space-y-2
    [:h2.text-2xl "retrying"]
    [retrying/main-panel]]

   [:section.space-y-2
    [:h2.text-2xl "clearing"]
    [clearing/main-panel]]

   [:section.space-y-2
    [:h2.text-2xl "input"]
    [input/main-panel]]

   [:section.space-y-2
    [:h2.text-2xl "wizard"]
    [wizard/main-panel]]])

(defn ^:dev/after-load mount-root []
  (re-frame/clear-subscription-cache!)
  (let [root-el (js/document.getElementById "app")]
    (rdom/unmount-component-at-node root-el)
    (rdom/render [main-panel] root-el)))

(defn init []
  (mount-root))
