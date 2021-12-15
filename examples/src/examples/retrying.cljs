(ns examples.retrying
  "Sample of an http request that is automatically retried a few times. See
  examples.retrying.request for details."
  (:require
   [re-frame.core :as re-frame]
   [examples.utils :as utils]
   [examples.retrying.request :as request]))

(def request-id [:tasks])
(def tasks-request
  (request/request request-id
                   ;; fails
                   {:uri        "https://httpbin.org/404"
                    :on-success [:event/tasks-fetched]}
                   ;; succeeds
                   #_{:uri        "https://httpbin.org/post"
                      :method     :post
                      :on-success [:event/tasks-fetched]}))

(re-frame/reg-event-fx
 :command/fetch-tasks
 (fn [_ _]
   {:http-xhrio tasks-request}))

(re-frame/reg-event-db
 :event/tasks-fetched
 [(request/success-after request-id)]
 (fn [db [_ tasks]]
   (assoc-in db [:data :tasks] tasks)))

(re-frame/reg-sub
 ::tasks
 (fn [db _]
   (get-in db [:data :tasks])))

(defn task-component [[k v]]
  [:details [:summary (pr-str k)] (pr-str v)])

(defn tasks-component []
  (let [[request-status error-status retry-status] (request/<status request-id)]
    (case request-status
      nil      [:button.border-2.border-black.px-2.py-1.hover:bg-gray-200
                {:type     "button"
                 :on-click #(request/>start request-id [:command/fetch-tasks])}
                "start"]
      :loading [:div "loading..."]
      :error   [:div "oops! something went wrong"
                (case error-status
                  :retrying (case retry-status
                              :waiting [:div "please wait"]
                              :loading [:div "retrying"])
                  :halted   [:div "gave up"])]
      :loaded  [:div (for [task (utils/<sub [::tasks])]
                       [task-component task])])))

(defn main-panel []
  [tasks-component])
