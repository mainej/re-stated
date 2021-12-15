(ns examples.clearing.request
  (:require
   [ajax.json :as ajax]
   [mainej.re-stated :as state]
   [examples.utils :as utils]
   [re-frame.core :as re-frame]
   [re-frame.utils :as rf.utils]))

(def default-request
  {:method          :get
   :format          (ajax/json-request-format)
   :response-format (ajax/json-response-format {:keywords? true})})

(defn request [request-path spec]
  (-> default-request
      (merge spec)
      (update :on-failure #(or % [::error request-path]))))

(def clearing-machine
  "A machine that finalizes resource loading a few moments after it succeeds or
  fails."
  (state/machine
   {:id      :clearing
    :initial :loading
    :states  {:loading    {:on {:error   :error
                                :success :loaded}}
              :error      {:after [{:delay  4000
                                    :target :finalizing}]}
              :loaded     {:after [{:delay  2000
                                    :target :finalizing}]}
              :finalizing {:entry (state/dispatch-in [:callbacks :finalize])}}}))

(defn start [db request-path]
  (state/initialize-in db (into [:requests] request-path) clearing-machine
                       {:callbacks {:finalize [::clear request-path]}}))

(defn success [db request-path]
  (state/transition-in db (into [:requests] request-path) clearing-machine :success))

(defn success-after [request-path]
  (state/transition-after (into [:requests] request-path) clearing-machine :success))

(defn error [db request-path]
  (state/transition-in db (into [:requests] request-path) clearing-machine :error))

(re-frame/reg-event-fx
 ::start
 (fn [{:keys [db]} [_ request-path request-spec]]
   {:db (start db request-path)
    :fx [[:http-xhrio (request request-path request-spec)]]}))

(re-frame/reg-event-db
 ::error
 (fn [db [_ request-path _error]] (error db request-path)))

(re-frame/reg-event-db
 ::clear
 (fn [db [_ request-path]]
   (rf.utils/dissoc-in db (into [:requests] request-path))))

(re-frame/reg-sub
 ::status
 (fn [db [_ request-path]]
   (-> db :requests (get-in request-path) state/state)))

(defn <status [request-path]
  (utils/<sub [::status request-path]))

(defn >start [request-path request-spec]
  (re-frame/dispatch [::start request-path request-spec]))
