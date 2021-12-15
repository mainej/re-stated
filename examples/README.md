This directory contains a re-frame app with examples of interactions that work
well when modeled as state machines.

These are just a starting place. You should adapt the machines showcased here to
your own policies.

Or build your own machines. The tricky part is defining the machine's states and
transitions. But once you have that, re-stated will help you quickly integrate
your machine's behavior into your re-frame app.

## Running the examples

In a shell in this directory:

```shell
$ npm install
$ npx shadow-cljs -A:cljs:cljs/dev server app
```

In another shell:

```shell
$ npx shadow-cljs -A:cljs:cljs/dev watch app
```

Then open http://localhost:8280.

## Request tracking

There are two examples of strategies for tracking HTTP requests.

Both examples are split into two pieces. The first is a namespace that aids in
starting a generic request and tracks whether it succeeds or fails. The second
is a re-frame component that uses the tools in that namespace to make one
particular request.

### Clearing requests

Sometimes you want to show a spinner while a request is in progress, then
briefly show a message after it succeeds or fails. To do this, you need to keep
the request status in the db for a few moments after it completes.

To see how you might do this, see
[`examples.clearing`](src/examples/clearing.cljs). It uses the tools defined in
[`examples.clearing.request`](src/examples/clearing/request.cljs).

Notice that `examples.clearing` is actually _less_ code than you would find in a
typical re-frame app that uses `:http-xhrio`.

### Retrying requests

Sometimes your users are on mobile devices with bad connections, or you
communicate with flaky APIs. Perhaps you need to retry requests when they fail.

To see how you might do this, see
[`examples.retrying`](src/examples/retrying.cljs). It uses the tools defined in
[`examples.retrying.request`](src/examples/retrying/request.cljs).

## Form inputs

Forms can have complex rules about the UI of their inputs. For example, to avoid
a "wall of red" it's common to show validation errors _only after_ an input has
been visited at least once.

See [`examples.input`](src/examples/input.cljs) for a state machine that tracks
how an input has been interacted with over time.

This example borrows its state names from [Final
Form](https://final-form.org/docs/final-form/types/FieldState).

## Wizards

Want to advance a user through a wizard, perhaps letting them retreat to prior steps?

See [`examples.wizard`](src/examples/wizard.cljs) for a state machine that
models a checkout flow.

This is a naively simple example—wizards can get wildly complex. But maybe you
can tame some of that complexity by modeling your wizard as a state machine.
