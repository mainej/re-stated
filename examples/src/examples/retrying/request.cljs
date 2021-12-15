(ns examples.retrying.request
  (:require
   [ajax.json :as ajax]
   [mainej.re-stated :as state]
   [examples.utils :as utils]
   [re-frame.core :as re-frame]
   [statecharts.core :as statecharts]
   [statecharts.utils :as sc.utils]))

(def default-request
  {:method          :get
   :format          (ajax/json-request-format)
   :response-format (ajax/json-response-format {:keywords? true})})

(defn request [request-path spec]
  (-> default-request
      (merge spec)
      (update :on-failure #(or % [::error request-path]))))

(def retrying-machine
  "A machine that tries to recover from errors by retrying. Retries twice before
  halting.

  Control the event that is retried by setting `:retry-evt` in the state-map."
  (state/machine
   {:id      :retrying
    :initial :loading
    :states  {:loading {:on {:error   :error
                             :success :loaded}}
              :error   {:initial :retrying
                        :states  {:retrying (letfn [(reset-retries [state-map _]
                                                      (assoc state-map :retries 2))
                                                    (update-retries [state-map _]
                                                      (update state-map :retries dec))
                                                    (retries-left? [{:keys [retries]} _]
                                                      (pos? retries))]
                                              {:entry   (statecharts/assign reset-retries)
                                               :initial :waiting
                                               :states  {:waiting {:after [{:delay  1000
                                                                            :target :loading}]}
                                                         :loading {:entry [(statecharts/assign update-retries)
                                                                           (state/dispatch-in [:retry-evt])]
                                                                   :on    {:error   [{:guard  retries-left?
                                                                                      :target :waiting}
                                                                                     [:> :error :halted]]
                                                                           :success [:> :loaded]}}}})
                                  :halted   {}}}
              :loaded  {}}}))

(defn start [db request-path request-event]
  (state/initialize-in db (into [:requests] request-path) retrying-machine
                       {:retry-evt request-event}))

(defn success [db request-path]
  (state/transition-in db (into [:requests] request-path) retrying-machine :success))

(defn success-after [request-path]
  (state/transition-after (into [:requests] request-path) retrying-machine :success))

(defn error [db request-path]
  (state/transition-in db (into [:requests] request-path) retrying-machine :error))

(re-frame/reg-event-fx
 ::start
 (fn [{:keys [db]} [_ request-path request-event]]
   {:db (start db request-path request-event)
    :fx [[:dispatch request-event]]}))

(re-frame/reg-event-db
 ::error
 (fn [db [_ request-path _error]] (error db request-path)))

(re-frame/reg-sub
 ::status
 (fn [db [_ request-path]]
   (-> db :requests (get-in request-path) state/state sc.utils/ensure-vector)))

(defn <status [request-path]
  (utils/<sub [::status request-path]))

(defn >start [request-path request-event]
  (re-frame/dispatch [::start request-path request-event]))
