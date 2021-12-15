(ns examples.utils
  (:require
    [re-frame.core :as re-frame]))

(defn <sub [subscription-v]
  @(re-frame/subscribe subscription-v))
