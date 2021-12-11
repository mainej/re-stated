(ns mainej.re-stated
  (:require
   [re-frame.core :as re-frame]
   [statecharts.clock]
   [statecharts.core :as statecharts]
   [statecharts.delayed]
   [statecharts.utils]))

(def ^:private state-map-path-key ::path)

(defn machine
  ([spec] (machine spec nil))
  ([spec {:keys [clock]}]
   ;; The atom is a work around for a circular dependency in clj-statecharts
   ;; between a machine and its scheduler.
   (let [!machine  (atom (statecharts/machine spec))
         scheduler (statecharts.delayed/make-scheduler
                    (fn [state delayed-event]
                      (re-frame/dispatch [:state/transition (state-map-path-key state) @!machine delayed-event]))
                    (or clock (statecharts.clock/wall-clock))
                    state-map-path-key)]
     (swap! !machine assoc :scheduler scheduler)
     @!machine)))

;;;; APP-DB

(def state :_state)

(defn- initialize [fsm state-map]
  (statecharts/initialize fsm {:context state-map}))

(defn initialize-in
  ([db state-map-path fsm] (initialize-in db state-map-path fsm nil))
  ([db state-map-path fsm state-map]
   (assoc-in db state-map-path (initialize fsm (assoc state-map state-map-path-key state-map-path)))))

(defn- transition [state fsm state-event]
  (statecharts/transition fsm state state-event))

(defn transition-in [db state-map-path fsm state-event]
  (update-in db state-map-path transition fsm state-event))

;;;; Event handlers

(re-frame/reg-event-db
 :state/initialize re-frame/trim-v
 (fn [db [state-map-path fsm state-map]]
   (initialize-in db state-map-path fsm state-map)))

(re-frame/reg-event-db
 :state/transition re-frame/trim-v
 (fn [db [state-map-path fsm state-event & data]]
   (let [state-event (statecharts.utils/ensure-event-map state-event)]
     (transition-in db state-map-path fsm (assoc state-event :data data)))))

;;;; Event augmenters

(defn initialize-after
  ([state-map-path fsm]
   (initialize-after state-map-path fsm nil))
  ([state-map-path fsm state-map]
   (re-frame/enrich
    (fn [db _]
      (initialize-in db state-map-path fsm state-map)))))

(defn transition-after [state-map-path fsm state-event]
  (re-frame/enrich
   (fn [db _]
     (transition-in db state-map-path fsm state-event))))

;;;; Actions

(defn dispatch-context-action [evt-path]
  (fn [state state-event]
    (re-frame/dispatch (into (get-in state evt-path)
                             [state state-event]))))
