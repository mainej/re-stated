# io.github.mainej/re-stated

State machines add organizational structure to code, potentially simplifying
complex interactions. There has been a small explosion of approaches to
integrating [clj-statecharts](https://lucywang000.github.io/clj-statecharts/)
with [re-frame](https://day8.github.io/re-frame/). But many of these approaches
are more convoluted than strictly necessary (see for example, my own earlier
attempt
[clj-statecharts-re-frame](https://github.com/mainej/clj-statecharts-re-frame).

Let's go back to basics to build a truly minimal integration. That is, let's
re-state ;) the problem:

## Analysis

### Terminology

First, a quick digression. Let's use this terminology.

* **fsm**, or **machine** A fsm is a specification of all the possible states
  and transitions.
* **state** A state is a vector like `[:connecting :handshake]`. It represents a
  particular position within the fsm: what state we're currently in.
* **state-map** A state-map is a map that holds a state, e.g. `{:_state
  [:connecting :handshake]}`. It can hold other contextual data which influences
  how the state is transitioned.

Though each of these things is immutable, we need a way to store and reference
different versions of a state-map as its state is transitioned through the fsm.
For this, we use re-frame.

### Requirements

A state-map is ... stateful. The word "state" is right there in its name.
Where do we store state in a re-frame app? In the app-db, usually. So,

* There should be tools to initialize and transition a state-map so it can
  be stored somewhere in the app-db.

And how do we modify the world in a re-frame app? Events, which lead to effects.

* A re-frame app should be able to dispatch events that initialize or transition
  a state-map.

How does a state machine interact with the outside world? Actions. 

* A state machine should be able to dispatch re-frame events via actions, i.e.
  when a state-map enters/exits/transitions between states.

## Implementation

To satisfy the first requirement, we want:
1. A function that, when given a db, db-path, fsm, and some (optional) context,
   initializes a state-map and stores it at the db-path. For use within an event
   handler.
   ```clojure
   (state/initialize-in db [:some :where] fsm {:contextual "data"})
   ```
2. A function that, when given a db, db-path, fsm and state event transitions
   the state-map stored at the db-path. For use within an event handler.
   ```clojure
   (state/transition-in db [:some :where] fsm :fsm-event)
   ```
3. Facilities for reading state and constructing subscriptions to state.
   ```clojure
   (rf/reg-sub
     :some-state
     :<- [:some-state-map]
     (fn [state-map _]
       (state/state state-map)))
   ```

For the second requirement, we want:
1. Event handlers that call these functions. For dispatching state events from a
   router, component or another event handler.
   ```clojure
   [:state/initialize [:some :where] fsm {:contextual "data"}]
   [:state/transition [:some :where] fsm :fsm-event]
   ```
2. Event interceptors that augment normal events such that when they're
   dispatched, a state-map is _also_ initialized or transitioned. (Before or
   after the event?) For dispatching regular events, events that should also
   trigger state events.
   ```clojure
   (state/initialize-after [:some :where] fsm {:contextual "data"})
   (state/transition-after [:some :where] fsm :fsm-event)
   ```

To satisfy the third requirement, we want:
1. clj-statecharts actions that dispatch fixed re-frame events. For use in
   transition/entry/exit actions.
   ```clojure
   (state/dispatch [:re-frame-event])
   ```
2. clj-statecharts actions that dispatch re-frame events stored in the
   state-map. For use in transition/entry/exit actions. By allowing the
   state-map to control the event, one state machine can be used to manage
   several state-maps.
   ```clojure
   (state/dispatch-in [:some :action/saved-in-context])
   ```

## License

Copyright © 2021 Jmaine

Distributed under the Eclipse Public License version 1.0.
