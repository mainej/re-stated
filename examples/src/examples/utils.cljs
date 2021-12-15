(ns examples.utils
  (:require
   [headlessui-reagent.core :as ui]
   [re-frame.core :as re-frame]))

(defn <sub [subscription-v]
  @(re-frame/subscribe subscription-v))

(defn toggle [{:keys [checked] :as props} label]
  [ui/switch-group
   [:div.flex
    [ui/switch (assoc props :class [(if checked :bg-blue-600 :bg-gray-200)
                                    :relative :inline-flex :items-center :h-6 :rounded-full :w-11 :transition-colors])
     [:span.inline-block.w-4.h-4.transform.bg-white.rounded-full.transition-transform
      {:class (if checked :translate-x-6 :translate-x-1)}]]
    [ui/switch-label {:class [:ml-3]} label]]])
