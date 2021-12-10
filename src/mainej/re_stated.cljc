(ns mainej.re-stated
  (:require [statecharts.core :as statecharts]
            [re-frame.core :as re-frame]))

;;;; APP-DB

(def state :_state)

(defn initialize [context fsm]
  (statecharts/initialize fsm {:context context}))

(defn initialize-in [db state-map-path context fsm]
  (assoc-in db state-map-path (initialize context fsm)))

(defn transition [state fsm state-event]
  (statecharts/transition fsm state state-event))

(defn transition-in [db state-map-path fsm state-event]
  (update-in db state-map-path transition fsm state-event))

;;;; Event handlers

(re-frame/reg-event-db
 :state/initialize
 re-frame/trim-v
 (fn [db [state-map-path context fsm]]
   (initialize-in db state-map-path context fsm)))

(re-frame/reg-event-db
 :state/transition
 re-frame/trim-v
 (fn [db [state-map-path fsm state-event]]
   (transition-in db state-map-path fsm state-event)))

;;;; Event augmenters

(defn initialize-after [state-map-path-or-fn state-context fsm]
  (let [state-map-path-fn (if (fn? state-map-path-or-fn)
                            state-map-path-or-fn
                            (constantly state-map-path-or-fn))]
    (re-frame/enrich
     (fn [db v]
       (initialize-in db (state-map-path-fn db v) state-context fsm)))))

(defn transition-after [state-map-path-or-fn fsm state-event]
  (let [state-map-path-fn (if (fn? state-map-path-or-fn)
                            state-map-path-or-fn
                            (constantly state-map-path-or-fn))]
    (re-frame/enrich
     (fn [db v]
       (transition-in db (state-map-path-fn db v) fsm state-event)))))
