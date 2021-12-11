(ns mainej.re-stated
  (:require
   [re-frame.core :as re-frame]
   [statecharts.clock]
   [statecharts.core :as statecharts]
   [statecharts.delayed]
   [statecharts.utils]))

(def ^:private state-map-path-key ::path)

(defn machine
  "Returns a clj-statecharts state machine described by the spec.

  These machines' delayed events will only work if their states are created by
  [[initialize-in]] or `:state/initialize`."
  ([spec] (machine spec nil))
  ([spec {:keys [clock]}]
   ;; The atom is a work around for a circular dependency in clj-statecharts
   ;; between a machine and its scheduler.
   ;; `transition` is passed a machine, so
   ;; in case the transition leads to delayed events, the machine has to carry
   ;; the scheduler. But the scheduler needs to refer to the machine in its
   ;; dispatch function. One fix would be for the machine to pass itself to the
   ;; `delayed/schedule` function (and on to the dispatch function).
   (let [!machine  (atom (statecharts/machine spec))
         scheduler (statecharts.delayed/make-scheduler
                    (fn [state delayed-event]
                      (re-frame/dispatch [:state/transition (state-map-path-key state) @!machine delayed-event]))
                    (or clock (statecharts.clock/wall-clock))
                    state-map-path-key)]
     (swap! !machine assoc :scheduler scheduler)
     @!machine)))

;;;; APP-DB

(def state
  "Access the clj-statecharts state stored in a state map."
  :_state)

(defn- initialize [state-map fsm]
  (statecharts/initialize fsm {:context state-map}))

(defn initialize-in
  "Initialize a state map using the given `fsm`, and store it in the `db` at
  `state-map-path`.

  If provided, `state-map` is used as the initial value of the state map (in
  clj-statecharts terminology, this is also known as the state \"context\".)
  This allows the state map to hold any additional data it may need when being
  transitioned. See, for example, [[dispatch-in]]."
  ([db state-map-path fsm] (initialize-in db state-map-path fsm nil))
  ([db state-map-path fsm state-map]
   (assoc-in db state-map-path (-> state-map
                                   (assoc state-map-path-key state-map-path)
                                   (initialize fsm)))))

(defn- transition [state-map fsm state-event]
  (statecharts/transition fsm state-map state-event))

(defn transition-in
  "Transition a state map stored in the `db` at `state-map-path`, using the
  given `fsm` and `state-event`."
  [db state-map-path fsm state-event]
  (update-in db state-map-path transition fsm state-event))

;;;; Event handlers

;; Initialize a state map in the app-db at `state-map-path`, as per [[initialize-in]]
(re-frame/reg-event-db
 :state/initialize re-frame/trim-v
 (fn [db [state-map-path fsm state-map]]
   (initialize-in db state-map-path fsm state-map)))

;; Transition a state map in the app-db at `state-map-path`, as per
;; [[transition-in]]. If the event vector is dispatched with any additional
;; result data, that will be available in the `:data` key of the `state-event`.
(re-frame/reg-event-db
 :state/transition re-frame/trim-v
 (fn [db [state-map-path fsm state-event & data]]
   (transition-in db state-map-path fsm (-> state-event
                                            statecharts.utils/ensure-event-map
                                            (assoc :data data)))))

;;;; Event augmenters

(defn initialize-after
  "Returns a re-frame interceptor that will initialize a state-map with the
  `fsm` and store it at the given `state-map-path`.

  See [[initialize-in]] for details about the optional `state-map`."
  ([state-map-path fsm]
   (initialize-after state-map-path fsm nil))
  ([state-map-path fsm state-map]
   (re-frame/enrich
    (fn [db _]
      (initialize-in db state-map-path fsm state-map)))))

(defn transition-after
  "Returns a re-frame interceptor that will transition a state-map stored at the
  given `state-map-path`, using the `fsm` and `state-event`."
  [state-map-path fsm state-event]
  (re-frame/enrich
   (fn [db _]
     (transition-in db state-map-path fsm state-event))))

;;;; Actions

(defn dispatch-by
  "Returns a clj-statecharts action that will dispatch to re-frame.

  `f`, a function of `state-map` and `state-event`, should return the re-frame
  event vector to be dispatched.

  The dispatched event vector will receive two additional params, again, the
  `state-map` and `state-event`. If the `state-event` was triggered by a
  transition that received any additional result data, that will be available in
  the `:data` key of the `state-event`."
  [f]
  (fn [state-map state-event]
    (re-frame/dispatch (into (f state-map state-event)
                             [state-map state-event]))))

(defn dispatch-in
  "Returns a clj-statecharts action that will dispatch a re-frame event vector
  that is stored in the `state-map` at the `evt-path`.

  See [[dispatch-by]] for details about the params appended to the event
  vector."
  [evt-path]
  (dispatch-by (fn [state-map _] (get-in state-map evt-path))))

(defn dispatch
  "Returns a clj-statecharts action that will dispatch a re-frame event vector
  `event-v`.

  See [[dispatch-by]] for details about the params appended to the event
  vector."
  [event-v]
  (dispatch-by (constantly event-v)))
