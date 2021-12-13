A small and powerful toolset that brings state machines to re-frame.

[![Clojars Project](https://img.shields.io/clojars/v/com.github.mainej/re-stated.svg)](https://clojars.org/com.github.mainej/re-stated)

State machines add organizational structure to code, potentially simplifying
complex interactions. There has been a small explosion of approaches to
integrating [`clj-statecharts`](https://lucywang000.github.io/clj-statecharts/)
with [`re-frame`](https://day8.github.io/re-frame/).

But many of these approaches are more convoluted than strictly necessary. (See
for example, my own earlier attempt
[`clj-statecharts-re-frame`](https://github.com/mainej/clj-statecharts-re-frame).)
Even `clj-statechart`s [own
integration](https://lucywang000.github.io/clj-statecharts/docs/integration/re-frame/)
with `re-frame` leaves something to be desired. It leaks memory and can't easily
manage several states
[ref](https://github.com/lucywang000/clj-statecharts/pull/7).

Let's go back to basics to build a truly minimal integration. That is, let's
re-state ;) the problem:

## Analysis

### Terminology

First, a quick digression. Let's use this terminology.

* **fsm**, or **machine**: A machine is a specification of all the possible
  states a process can be in and the transitions between those states.
* **state**: A state is a keyword or vector like `[:connecting :handshake]`. It
  represents a particular position within the machine: what state we're currently
  in.
* **state-map**: A state-map is a map that holds a state, e.g. `{:_state
  [:connecting :handshake]}`. It can hold other contextual data which influences
  how the state is transitioned.

Though each of these things is immutable, we need a way to store and reference
how state changes over time. For this, we use re-frame.

### Requirements

A state-map is ... stateful. The word "state" is right there in its name.
Where do we store state in a re-frame app? In the app-db, usually. So,

* We need tools to initialize and transition a state-map and store it in the
  app-db.

And how do we modify the world in a re-frame app? Events, which lead to effects.

* We should be able to dispatch re-frame events that initialize or transition a
  state-map.

How does a state machine interact with the outside world? Actions. 

* A state machine should be able to dispatch re-frame events via actions, i.e.
  when a state-map enters/exits/transitions between states.

Believe it or not, that's enough to build fairly complex state machines that
interact with re-frame.

## Implementation

To satisfy the first requirement, we want:
1. A function that, when given a db, db-path, fsm, and some (optional) context,
   initializes a state-map and stores it at the db-path. We'll use this function
   within event handlers.
   ```clojure
   (state/initialize-in db [:some :where] fsm {:contextual "data"})
   ```
2. A function that, when given a db, db-path, fsm and state event transitions
   the state-map stored at the db-path. We'll use this function within event
   handlers.
   ```clojure
   (state/transition-in db [:some :where] fsm :fsm-event)
   ```
3. Facilities for reading and constructing subscriptions to state.
   ```clojure
   (re-frame/reg-sub
     :some-state
     (fn [db _] (get-in db [:some :where state/state])))
   ```

For the second requirement, we want:
1. Pre-defined event handlers that call these functions. We'll dispatch these
   events from routers, components or other event handlers.
   ```clojure
   [:state/initialize [:some :where] fsm {:contextual "data"}]
   [:state/transition [:some :where] fsm :fsm-event]
   ```
2. Event interceptors that augment normal event handlers such that when they're
   dispatched, a state-map is _also_ initialized or transitioned. We'll use
   these to enhance existing events.
   ```clojure
   (state/initialize-after [:some :where] fsm {:contextual "data"})
   (state/transition-after [:some :where] fsm :fsm-event)
   ```

To satisfy the third requirement, we want:
1. clj-statecharts actions that dispatch fixed re-frame events. We'll use these
   in state machines, in transition/entry/exit actions.
   ```clojure
   (state/dispatch [:re-frame-event])
   ```
2. clj-statecharts actions that dispatch re-frame events stored in the
   state-map. We'll use these in state machines, in transition/entry/exit
   actions. By allowing the state-map to control the event, one state machine
   can be used to manage several state-maps.
   ```clojure
   (state/dispatch-in [:some :action/saved-in-context])
   ```

This is the contents of the `re-stated` toolchest. Now let's build something.

## Examples

### Simple HTTP progress tracking

Suppose we fetch data from an API. We've already set up our request and response
like so:

```clojure
(re-frame/reg-event-fx
 :command/fetch-customers
 (fn [_ _]
   {:http-xhrio {:uri             "http://example.com/customers"
                 :method          :get
                 :response-format (ajax/json-response-format {:keywords? true})
                 :on-success      [:event/customers-fetched]
                 :on-failure      [:event/customers-fetch-failed]}}))
                  
(re-frame/reg-event-db
 :event/customers-fetched
 (fn [db [_ customers]]
   (assoc-in db [:data :customers] customers)))

(re-frame/reg-event-db
 :event/customers-fetch-failed
 ;; no-op
 (fn [db [_ _error]] db))

(re-frame/reg-sub
 :customers
 (fn [db _]
   (get-in db [:data :customers])))
```

Now we'd like to give some feedback while the request is running and when it
completes successfully or unsuccessfully.

```clojure
(require '[mainej.re-stated :as state])

(def loading-machine
  "A machine that keeps track of whether an attempt at loading
  succeeded or failed."
  (state/machine
   {:id      :loading
    :initial :loading
    :states  {:loading {:on {:error   :error
                             :success :loaded}}
              :error   {}
              :loaded  {}}}))
              
(re-frame/reg-event-fx
 :command/fetch-customers
 [(state/initialize-after [:requests :customers] loading-machine)]
 ;; same as before
 ,,,)
                  
(re-frame/reg-event-db
 :event/customers-fetched
 [(state/transition-after [:requests :customers] loading-machine :success)]
 ;; same as before
 ,,,)

(re-frame/reg-event-db
 :event/customers-fetch-failed
 [(state/transition-after [:requests :customers] loading-machine :error)]
 ;; same as before
 ,,,)
 
(re-frame/reg-sub
 :customers-request-status
 (fn [db _]
   (get-in db [:requests :customers state/state])))

(defn customers-component []
  (case @(re-frame/subscribe [:customers-request-status])
    nil      [:button {:type     "button"
                       :on-click #(re-frame/dispatch [:command/fetch-customers])}
              "start"]
    :loading [:div "loading..."]
    :error   [:div "oops! something went wrong"]
    :loaded  [:div (for [customer @(re-frame/subscribe [:customers])]
                     ^{:key (:id customer)}
                     [customer-component customer])]))
```

Nice! Now we can give users feedback while they're waiting for the fetch to
complete. With a little refactoring it should be easy to add this pattern to all
of our requests.

### Many simultaneous requests

What if we have so many customers with so much data that our customers request
is really slow? Perhaps we could load summary data in the first request, then in
the background fetch details for each customer.

We'll want a loading/success/failure message for each details request. There are
several ways to do this, but let's continue using the `loading-machine` we
created earlier.

```clojure
(re-frame/reg-event-fx
 :event/customers-fetched
 [(state/transition-after [:requests :customers] loading-machine :success)]
 (fn [{:keys [db]} [_ customers]]
   {:db (assoc-in db [:data :customers] customers)
    ;; start the background requests for customer details
    :fx (for [customer customers]
          [:dispatch-later {:ms 500 :dispatch [:command/fetch-customer (:id customer)]}])}))
          
(re-frame/reg-event-fx
 :command/fetch-customer
 (fn [{:keys [db]} [_ id]]
   {:db (state/initialize-in db [:requests :customer id] loading-machine)
    :http-xhrio {:uri             (str "http://example.com/customers/" id)
                 :method          :get
                 :response-format (ajax/json-response-format {:keywords? true})
                 :on-success      [:event/customer-fetched id]
                 :on-failure      [:event/customer-fetch-failed id]}}))
                  
(re-frame/reg-event-db
 :event/customer-fetched
 (fn [db [_ id customer]]
   (-> db
       (assoc-in [:data :customer-details id] customer)
       (state/transition-in [:requests :customer id] loading-machine :success))))

(re-frame/reg-event-db
 :event/customer-fetch-failed
 (fn [db [_ id _error]]
  (state/transition-in db [:requests :customers id] loading-machine :error)))

(re-frame/reg-sub 
 :customer-request-status
 (fn [db [_ id]] 
   (get-in db [:requests :customer id state/state])))

(re-frame/reg-sub 
 :customer-details
 (fn [db [_ id]]
   (get-in db [:data :customer-details id])))
 
(defn customer-component [{:keys [id]}]
  (let [request-status   @(re-frame/subscribe [:customer-request-status id])
        customer-details @(re-frame/subscribe [:customer-details id])]
    (case request-status
      nil      [:div "summary loaded..."]
      :loading [:div "loading details..."]
      :error   [:div "oops! something went wrong"]
      :loaded  [:div "hi " (:nickname customer-details) "!"])))
```

This was a little more complicated because we needed a customer `id` to
initialize, transition and read each request. But still, not so bad.

### Automatic retries

What if we notice our API is a little flaky and we want to automatically retry a
few times before giving up? This sounds like something we can model in a state
machine. It'll be similar to our `loading-machine`, but with a few more bells
and whistles:

```clojure
(require '[statecharts.core :as statecharts])

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
              
;; The fetch event is split in two. One, to start the state machine
;; and enqueue the initial request.
(re-frame/reg-event-fx
 :command/start-fetch-customers
 [(state/initialize-after [:requests :customers] retrying-machine
                          ;; on retry, re-fetch the customers
                          {:retry-evt [:command/fetch-customers]})]
 (fn [_ _]
   {:fx [[:dispatch [:command/fetch-customers]]]}))
                 
;; And two, to actually place the request. This is the command that is
;; retried.
(re-frame/reg-event-fx
 :command/fetch-customers
 (fn [_ _]
   {:http-xhrio {:uri             "http://example.com/customers"
                 :method          :get
                 :response-format (ajax/json-response-format {:keywords? true})
                 :on-success      [:event/customers-fetched]
                 :on-failure      [:event/customers-fetch-failed]}}))
                  
(re-frame/reg-event-db
 :event/customers-fetched
 [(state/transition-after [:requests :customers] retrying-machine :success)]
 (fn [db [_ customers]]
   (assoc-in db [:data :customers] customers)))

(re-frame/reg-event-db
 :event/customers-fetch-failed
 ;; This error will start the next request, if there are any retries left.
 [(state/transition-after [:requests :customers] retrying-machine :error)]
 (fn [db [_ _error]]
   db))
   
(re-frame/reg-sub
 :customers-request-status
 (fn [db _]
   (statecharts.utils/ensure-vector (get-in db [:requests :customers state/state]))))

(defn customers-component []
  (let [[request-status error-status retry-status] @(re-frame/subscribe [:customers-request-status])]
    (case request-status
      nil      [:button {:type     "button"
                         :on-click #(re-frame/dispatch [:command/start-fetch-customers])}
                "start"]
      :loading [:div "loading..."]
      :error   [:div "oops! something went wrong"
                (case error-status
                  :retrying (case retry-status
                              :waiting [:div "please wait"]
                              :loading [:div "retrying"])
                  :halted   [:div "gave up"])]
      :loaded  [:div (for [customer @(re-frame/subscribe [:customers])]
                       ^{:key (:id customer)}
                       [customer-component customer])])))
```

Need to poll an API for updates? Or track how a user has interacted with an
input field? Or walk a user through a wizard? These are great use cases for
state machines too. What other tools will you build?

## Inspiration

* [`clj-statecharts`](https://lucywang000.github.io/clj-statecharts/) lays the
  groundwork for a data-driven model of state machines. It in turn is influenced
  by [`xstate`](https://github.com/statelyai/xstate) and
  [`scxml`](https://www.w3.org/TR/scxml/).
* [`glimt`](https://github.com/ingesolvoll/glimt) uses `clj-statecharts` to
  manage http requests. Many of the examples in this project mirror `glimt`'s
  capabilities, because I wanted to make sure `re-stated` was at least as
  powerful. With its narrower focus, `glimt` can be more concise in its domain.

## Development

See [CONTRIBUTING.md](CONTRIBUTING.md).

## License

Copyright © 2021 Jacob Maine

Distributed under the Eclipse Public License version 1.0.
