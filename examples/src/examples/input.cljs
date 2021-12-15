(ns examples.input
  "Sample of tracking how a user has interacted with an input field."
  (:require [examples.utils :as utils]
            [re-frame.core :as re-frame]
            [reagent.core :as reagent]
            [headlessui-reagent.core :as ui]
            [mainej.re-stated :as state]))

(defn state-pristine? [{:keys [value initial-value]} _]
  (= value initial-value))

(def input-machine
  (state/machine
   {:id      :input
    :type    :parallel
    :regions {;; ever entered the input?
              :field/visited?  {:initial :false
                                :states  {:false {:on {:focus-gained :true}}
                                          :true  {}}}
              ;; currently in the input?
              :field/active?   {:initial :false
                                :states  {:false {:on {:focus-gained :true}}
                                          :true  {:on {:focus-lost :false}}}}
              ;; ever left the input?
              :field/touched?  {:initial :false
                                :states  {:false {:on {:focus-lost :true}}
                                          :true  {}}}
              ;; ever changed the input?
              :field/modified? {:initial :false
                                :states  {:false {:on {:changed :true}}
                                          :true  {:on {}}}}
              ;; is the input's value the same as was originally?
              :field/pristine? {:initial :true
                                :states  {:false {:on {:changed [{:guard  state-pristine?
                                                                  :target :true}]}}
                                          :true  {:on {:changed  [{:guard  (complement state-pristine?)
                                                                   :target :false}]}}}}}}))

(re-frame/reg-event-db
 :init-input
 (fn [db [_ input-id val]]
   (state/initialize-in db [:inputs input-id] input-machine
                        {:value         val
                         :initial-value val})))

(re-frame/reg-event-db
 :change-input
 (fn [db [_ input-id val]]
   (-> db
       (assoc-in [:inputs input-id :value] val)
       (state/transition-in [:inputs input-id] input-machine :changed))))

(re-frame/reg-sub
 :input
 (fn [db [_ id]]
   (get-in db [:inputs id])))

(defn input-evt [input-id evt]
  (re-frame/dispatch [::state/transition [:inputs input-id] input-machine evt]))

(defn on-focus [input-id] (input-evt input-id :focus-gained))
(defn on-blur [input-id] (input-evt input-id :focus-lost))
(defn on-change [input-id val] (re-frame/dispatch [:change-input input-id val]))

(defn toggle [label enabled?]
  [ui/switch-group
   [:div.flex
    [ui/switch {:checked  enabled?
                :disabled true
                :class    [(if enabled? :bg-blue-600 :bg-gray-200)
                           :relative :inline-flex :items-center :h-6 :rounded-full :w-11 :transition-colors]}
     [:span.inline-block.w-4.h-4.transform.bg-white.rounded-full.transition-transform
      {:class (if enabled? :translate-x-6 :translate-x-1)}]]
    [ui/switch-label {:class [:ml-3]} label]]])

(defn main-panel []
  (reagent/with-let [input-id :lang
                     _ (re-frame/dispatch [:init-input input-id "Clojure"])
                     props {:on-focus  #(on-focus input-id)
                            :on-blur   #(on-blur input-id)
                            :on-change #(on-change input-id (.-value (.-target %)))}]
    (let [input       (utils/<sub [:input input-id])
          input-state (state/state input)]
      [:div.space-y-4
       [:label [:div "Preferred language"]
        [:input.px-4.py-2.border.border-black
         (assoc props :value (:value input))]]
       [:div.flex.flex-col.space-y-2
        (for [state-k [:field/visited?
                       :field/active?
                       :field/touched?
                       :field/modified?
                       :field/pristine?]]
          ^{:key state-k}
          [toggle (name state-k) (= :true (state-k input-state))])]])))
