(ns mainej.re-stated-test
  (:require [clojure.test :as t]
            [day8.re-frame.test :as rf.t]
            [re-frame.core :as rf]
            [mainej.re-stated :as state]
            [statecharts.core :as statecharts]))

(def http-machine
  (statecharts/machine
   {:id      :http
    :initial :loading
    :states  {:loading {:on {:error   :error
                             :success :loaded}}
              :error   {:initial :halted
                        :states  {:halted {}}}
              :loaded  {}}}))

(defn <sub [subscription-v]
  @(rf/subscribe subscription-v))

(rf/reg-sub :ex1/state (fn [db _] (get-in db [:ex1/state-map state/state])))

(t/deftest control-via-re-stated-events
  (rf.t/run-test-sync
   (rf/dispatch [:state/initialize [:ex1/state-map] {} http-machine])
   (t/is (= :loading
            (<sub [:ex1/state])))
   (rf/dispatch [:state/transition [:ex1/state-map] http-machine :error])
   (t/is (= [:error :halted]
            (<sub [:ex1/state])))))

(rf/reg-sub :ex2/state (fn [db _] (get-in db [:ex2/state-map state/state])))

(rf/reg-event-db
 :ex2.command/start-http
 [(state/initialize-after [:ex2/state-map] {} http-machine)]
 (fn [db _]
   db))

(rf/reg-event-db
 :ex2.event/http-error
 [(state/transition-after [:ex2/state-map] http-machine :error)]
 (fn [db _]
   db))

(t/deftest control-via-augmented-re-frame-events
  (rf.t/run-test-sync
   (rf/dispatch [:ex2.command/start-http])
   (t/is (= :loading
            (<sub [:ex2/state])))
   (rf/dispatch [:ex2.event/http-error])
   (t/is (= [:error :halted]
            (<sub [:ex2/state])))))

(rf/reg-sub :ex3/state (fn [db [_ id]] (get-in db [:ex3/state-maps id state/state])))

(rf/reg-event-db
 :ex3.command/start-http
 [(state/initialize-after (fn [_ [_ id]] [:ex3/state-maps id]) {} http-machine)]
 (fn [db _]
   db))

(rf/reg-event-db
 :ex3.event/http-error
 [(state/transition-after (fn [_ [_ id]] [:ex3/state-maps id]) http-machine :error)]
 (fn [db _]
   db))

(t/deftest control-via-augmented-re-frame-events-with-dynamic-state-path
  (rf.t/run-test-sync
   (rf/dispatch [:ex3.command/start-http 1])
   (t/is (= :loading
            (<sub [:ex3/state 1])))
   (rf/dispatch [:ex3.event/http-error 1])
   (t/is (= [:error :halted]
            (<sub [:ex3/state 1])))))
