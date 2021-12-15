(ns examples.wizard
  (:require
    [mainej.re-stated :as state]
    [statecharts.core :as statecharts]
    [re-frame.core :as re-frame]
    [reagent.core :as reagent]
    [examples.utils :as utils]))


(def wizard-machine
  "A machine that walks through a checkout flow."
  (letfn [(complete [{step :_prev-state :as state} _event]
            (-> state
                (update :complete (fnil conj #{}) step)
                (update :incomplete (fnil disj #{}) step)))
          (incomplete [{step :_prev-state :as state} _event]
            (-> state
                (update :complete (fnil disj #{}) step)
                (update :incomplete (fnil conj #{}) step)))
          (next-step [step]
            {:target  step
             :actions (statecharts/assign complete)})
          (prev-step [step]
            {:target  step
             :actions (statecharts/assign incomplete)})]
    (state/machine
     {:id      :wizard
      :initial :step/review-cart
      :states  {:step/review-cart      {:on {:prev :step/review-cart ;; no-op
                                             :next (next-step :step/shipping-address)}}
                :step/shipping-address {:on {:prev (prev-step :step/review-cart)
                                             :next (next-step :step/payment)}}
                :step/payment          {:on {:prev (prev-step :step/shipping-address)
                                             :next (next-step :step/done)}}
                :step/done             {:on {:prev :step/payment
                                             :next :step/done ;; no-op
                                             }}}})))

(re-frame/reg-sub
 :wizard
 (fn [db _] (get db :wizard)))

(defmulti step-component
  (fn [wizard {:keys [step]}]
    (cond
      (= step (state/state wizard))         :current
      (contains? (:incomplete wizard) step) :incomplete
      (contains? (:complete wizard) step)   :complete
      :else                                 :upcoming)))

(defmethod step-component :complete [_ {:keys [label]}]
  [:div
   [:span.flex.items-start
    [:span.flex-shrink-0.relative.h-5.w-5.flex.items-center.justify-center
     ;; Heroicon: solid/check-circle
     [:svg.h-full.w-full.text-blue-600
      {:aria-hidden "true",
       :fill        "currentColor",
       :viewBox     "0 0 20 20"}
      [:path {:clip-rule "evenodd",
              :d         "M10 18a8 8 0 100-16 8 8 0 000 16zm3.707-9.293a1 1 0 00-1.414-1.414L9 10.586 7.707 9.293a1 1 0 00-1.414 1.414l2 2a1 1 0 001.414 0l4-4z",
              :fill-rule "evenodd"}]]]
    [:span.ml-3.text-sm.font-medium.text-gray-500
     label]]])

(defmethod step-component :current [_ {:keys [label]}]
  [:div.flex.items-start
   {:aria-current "step"}
   [:span.flex-shrink-0.h-5.w-5.relative.flex.items-center.justify-center
    {:aria-hidden "true"}
    [:span.absolute.h-4.w-4.rounded-full.bg-blue-200]
    [:span.relative.block.w-2.h-2.bg-blue-600.rounded-full]]
   [:span.ml-3.text-sm.font-medium.text-blue-600
    label]])

(defmethod step-component :incomplete [_ {:keys [label]}]
  [:div.flex.items-start
   [:span.flex-shrink-0.h-5.w-5.relative.flex.items-center.justify-center
    {:aria-hidden "true"}
    [:span.w-2.h-2.bg-gray-600.rounded-full]]
   [:span.ml-3.text-sm.font-medium.text-gray-500.italic
    label]])

(defmethod step-component :upcoming [_ {:keys [label]}]
  [:div.flex.items-start
   [:div.flex-shrink-0.h-5.w-5.relative.flex.items-center.justify-center
    {:aria-hidden "true"}
    [:div.h-2.w-2.bg-gray-300.rounded-full]]
   [:span.ml-3.text-sm.font-medium.text-gray-500
    label]])

(defn main-panel []
  (reagent/with-let [_ (re-frame/dispatch [::state/initialize [:wizard] wizard-machine])]
    (let [wizard (utils/<sub [:wizard])]
      [:div.space-x-4.flex
       [:div.space-x-2
        [:button.border-2.border-black.px-2.py-1.hover:bg-gray-200
         {:on-click #(re-frame/dispatch [::state/transition [:wizard] wizard-machine :prev])}
         "Prev"]
        [:button.border-2.border-black.px-2.py-1.hover:bg-gray-200
         {:on-click #(re-frame/dispatch [::state/transition [:wizard] wizard-machine :next])}
         "Next"]]
       [:nav
        {:aria-label "Progress"}
        [:ol.space-y-6
         {:role "list"}
         (for [step [{:step :step/review-cart      :label "Review Cart"}
                     {:step :step/shipping-address :label "Shipping"}
                     {:step :step/payment          :label "Payment"}]]
           ^{:key (:step step)}
           [:li [step-component wizard step]])]]])))
