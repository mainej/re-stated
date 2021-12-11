(ns mainej.re-stated-test
  (:require
   [clojure.test :as t]
   [day8.re-frame.test :as rf.t]
   [mainej.re-stated :as state]
   [statecharts.core :as statecharts]
   [statecharts.sim]
   [re-frame.core :as rf]))

(def loading-machine
  "A machine that keeps track of whether an attempt at loading succeeded or failed"
  (state/machine
   {:id      :loading
    :initial :loading
    :states  {:loading {:on {:error   :error
                             :success :loaded}}
              :error   {:initial :halted
                        :states  {:halted {}}}
              :loaded  {}}}))

(defn <sub [subscription-v]
  @(rf/subscribe subscription-v))

(rf/reg-sub :ex1/state (fn [db _] (get-in db [:ex1/state-map state/state])))

(t/deftest control-state-via-re-stated-events
  (rf.t/run-test-sync
   (rf/dispatch [:state/initialize [:ex1/state-map] loading-machine])
   (t/is (= :loading
            (<sub [:ex1/state])))
   (rf/dispatch [:state/transition [:ex1/state-map] loading-machine :error])
   (t/is (= [:error :halted]
            (<sub [:ex1/state])))))

(rf/reg-sub :ex2/state (fn [db _] (get-in db [:ex2/state-map state/state])))

(rf/reg-event-db
 :ex2.command/start-http
 [(state/initialize-after [:ex2/state-map] loading-machine)]
 (fn [db _]
   db))

(rf/reg-event-db
 :ex2.event/http-error
 [(state/transition-after [:ex2/state-map] loading-machine :error)]
 (fn [db _]
   db))

(t/deftest control-state-via-augmented-app-events
  (rf.t/run-test-sync
   (rf/dispatch [:ex2.command/start-http])
   (t/is (= :loading
            (<sub [:ex2/state])))
   (rf/dispatch [:ex2.event/http-error])
   (t/is (= [:error :halted]
            (<sub [:ex2/state])))))

(def clock (statecharts.sim/simulated-clock))
(defn advance-clock [ms]
  (statecharts.sim/advance clock ms))

(def retrying-machine
  "A machine that retries twice (by default) before halting.

  Always retries at least once.

  Control the number of retries and the event that is retried by setting
  `:retries` and `:retry-evt` respectively in the state-map. "
  (state/machine
   {:id      :retrying
    :initial :loading
    :states  {:loading {:on {:error   :error
                             :success :loaded}}
              :error   {:initial :retrying
                        :states  {:retrying (letfn [(reset-retries [state-map _]
                                                      (update state-map :retries #(or % 2)))
                                                    (update-retries [state-map _]
                                                      (update state-map :retries dec))
                                                    (retries-left? [{:keys [retries]} _]
                                                      (pos? retries))]
                                              {:entry   (statecharts/assign reset-retries)
                                               :initial :waiting
                                               :states  {:waiting {:after [{:delay  1000
                                                                            :target :loading}]}
                                                         :loading {:entry [(statecharts/assign update-retries)
                                                                           (state/dispatch-context-action [:retry-evt])]
                                                                   :on    {:error   [{:guard  retries-left?
                                                                                      :target :waiting}
                                                                                     [:> :error :halted]]
                                                                           :success [:> :loaded]}}}})
                                  :halted   {}}}
              :loaded  {}}}
   {:clock clock}))

(rf/reg-sub :ex3/state-map (fn [db [_ id]] (get-in db [:ex3/state-maps id])))
(rf/reg-sub :ex3/state (fn [db [_ id]] (get-in db [:ex3/state-maps id state/state])))
(rf/reg-sub :ex3/requests (fn [db [_ id]] (get-in db [:requests id])))

(rf/reg-event-fx
 :ex3.command/start-http
 (fn [{:keys [db]} [_ id]]
   (let [send-http-event [:ex3.command/send-http id]]
     {:db (state/initialize-in db [:ex3/state-maps id]
                               retrying-machine
                               {:retry-evt send-http-event})
      :fx [[:dispatch send-http-event]]})))

(rf/reg-event-fx
 :ex3.command/send-http
 (fn [{:keys [db]} [_ id]]
   ;; In a real app, this event handler would construct an :http-xhrio:
   #_{:http-xhrio {,,,
                   :on-success [:ex3.event/http-success id]
                   :on-failure [:ex3.event/http-error id]}}
   {:db (update-in db [:requests id] (fnil inc 0))
    :fx [[:dispatch [:ex3.event/http-error id]]]}))

(rf/reg-event-db
 :ex3.event/http-error
 (fn [db [_ id]]
   (state/transition-in db [:ex3/state-maps id] retrying-machine :error)))

(t/deftest control-re-frame-via-state-machine-actions
  (rf.t/run-test-sync
   (rf/dispatch [:ex3.command/start-http 1]) ;; initial request, immediately fails
   (t/is (= 1 (<sub [:ex3/requests 1])))
   (t/is (= [:error :retrying :waiting] (<sub [:ex3/state 1])))
   (advance-clock 1000) ;; first retry, immediately fails
   (t/is (= 2 (<sub [:ex3/requests 1])))
   (t/is (= [:error :retrying :waiting] (<sub [:ex3/state 1])))
   (advance-clock 1000) ;; second retry, immediately fails
   (t/is (= 3 (<sub [:ex3/requests 1])))
   (t/is (= [:error :halted] (<sub [:ex3/state 1])))))
