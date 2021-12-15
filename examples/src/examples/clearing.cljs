(ns examples.clearing
  "Sample of an http request that is automatically cleared after a few moment.
  See examples.clearing.request for details."
  (:require
   [examples.clearing.request :as request]
   [examples.utils :as utils]
   [re-frame.core :as re-frame]))

(def request-id [:items])

(def request-spec
  ;; fails
  {:uri        "https://httpbin.org/404"
   :on-success [:event/items-fetched]}
  ;; succeeds
  #_{:uri        "https://httpbin.org/post"
     :method     :post
     :on-success [:event/items-fetched]})

(re-frame/reg-event-db
 :event/items-fetched
 [(request/success-after request-id)]
 (fn [db [_ items]]
   (assoc-in db [:data :items] items)))

(re-frame/reg-sub
 ::items
 (fn [db _]
   (get-in db [:data :items])))

(defn item-component [[k v]]
  [:details [:summary (pr-str k)] (pr-str v)])

(defn items-component []
  [:div
   (case (request/<status request-id)
     nil         [:button.border-2.border-black.px-2.py-1.hover:bg-gray-200
                  {:type     "button"
                   :on-click #(request/>start request-id request-spec)}
                  "start"]
     :loading    [:div "loading..."]
     :error      [:div "oops! something went wrong"]
     :loaded     [:div "loaded"]
     :finalizing [:div "clearing"])
   [:div (for [item (utils/<sub [::items])]
           [item-component item])]])

(defn main-panel []
  [items-component])
