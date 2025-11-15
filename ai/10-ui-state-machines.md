
# UI State Machines

## Overview
UI State Machines (UISM) provide a powerful way to organize complex UI logic and interactions. They're particularly useful for coordinating operations across multiple components.

## Core Concepts

### State Machine Benefits
- **Explicit states**: Clear representation of application phases
- **Controlled transitions**: Only defined transitions are allowed
- **Centralized logic**: Complex workflows in one place
- **Actor model**: UI components play "roles" in the machine
- **Debugging**: State and transitions visible in Fulcro Inspect

### When to Use UISM
- **Login/authentication flows**: Login, logout, session management
- **Form workflows**: Multi-step forms, validation, submission
- **Modal dialogs**: Open, edit, save, cancel patterns
- **CRUD operations**: Create, read, update, delete sequences
- **Complex UI interactions**: Multiple components need coordination

## Basic State Machine Definition

### Machine Structure
```clojure
(defstatemachine session-machine
  {::uism/actor-names
   #{:actor/current-session}

   ::uism/aliases
   {:logged-in? [::session :logged-in?]}

   ::uism/states
   {:initial
    {::uism/handler
     (fn [env]
       ;; Initial state handler runs on ::uism/started event
       (-> env
         (uism/assoc-aliased :logged-in? false)
         (uism/activate :state/checking-session)))}

    :state/checking-session
    {::uism/events
     {:event/session-valid   {::uism/target-state :state/logged-in}
      :event/session-invalid {::uism/target-state :state/logged-out}}}

    :state/logged-in
    {::uism/events
     {:event/logout {:handler logout-handler}}}

    :state/logged-out
    {::uism/events
     {:event/login {:handler login-handler}}}}})
```

### Key Elements
- **Actor names**: Declared with `::uism/actor-names` as a set of keywords representing component roles
- **Aliases**: Convenient paths to data in actor entities
- **States**: Discrete phases of the workflow
- **Events**: Triggers that cause transitions
- **Handlers**: Functions that execute during transitions

## Working with Actors

### Actor Declaration
```clojure
;; Declare actor names in the machine definition
(defstatemachine session-machine
  {::uism/actor-names #{:actor/current-session :actor/login-form}
   ...})
```

### Actor Assignment at Runtime
```clojure
;; Start machine with actual actors (idents or component classes)
(uism/begin! this session-machine ::session-sm
  {:actor/current-session (uism/with-actor-class [:session/id :main] Session)
   :actor/login-form      LoginForm})
```

### Actor Communication
```clojure
;; In event handler
(defn login-handler [env]
  (let [session-ident (uism/actor->ident env :actor/current-session)]
    (-> env
      (uism/reset-actor-ident :actor/current-session updated-ident)
      (uism/activate :state/logged-in))))
```

## Event Handling

### Event Structure
```clojure
;; Simple event with target state (no handler needed)
{:event/cancel {::uism/target-state :state/idle}}

;; Event with handler
{:event/submit
 {::uism/handler submit-handler}}

;; Event with handler and target state (handler runs first)
{:event/save
 {::uism/handler   save-handler
  ::uism/target-state :state/saving}}

;; Event with predicate (gate condition)
{:event/submit
 {::uism/event-predicate (fn [env] (uism/alias-value env :form-valid?))
  ::uism/handler         submit-handler
  ::uism/target-state    :state/submitting}}
```

### Event Handlers
```clojure
(defn login-handler [env]
  (-> env
    ;; Update application state via aliases
    (uism/assoc-aliased :logged-in? true)
    ;; Trigger remote operation
    (uism/trigger-remote-mutation :actor/login-form 'api/authenticate
      {:credentials       (uism/alias-value env :credentials)
       ::uism/ok-event    :event/login-ok
       ::uism/error-event :event/login-failed})
    ;; Transition to next state
    (uism/activate :state/checking)))
```

## Triggering Events

### From Components
```clojure
(defsc LoginForm [this props]
  {:query [...]}
  (dom/div
    (dom/button
      {:onClick #(uism/trigger! this ::session-sm :event/login {:credentials creds})}
      "Login")))
```

### From Mutations
```clojure
(defmutation authenticate [params]
  (action [env]
    (uism/trigger! env ::session-sm :event/check-session)))
```

## Machine Data and Aliases

### Storing Machine State
```clojure
;; Set machine data
(uism/assoc-aliased env :current-step 2)
(uism/update-aliased env :error-count inc)

;; Get machine data
(uism/alias-value env :current-step)
```

### Alias Configuration
```clojure
::uism/aliases
{:logged-in?    [:actor/current-session :session/logged-in?]
 :current-user  [:actor/current-session :session/current-user]
 :loading?      [:actor/current-session :ui/loading?]}
```

## Remote Operations

### Triggering Remote Mutations
```clojure
(defn save-handler [env]
  (-> env
    (uism/activate :state/saving)
    (uism/trigger-remote-mutation :actor/form 'api/save-item
      {:item-data       (uism/alias-value env :form-data)
       ::m/returning    (uism/actor-class env :actor/form)
       ::uism/ok-event  :event/save-ok
       ::uism/error-event :event/save-failed})))
```

### Handling Results
```clojure
:state/saving
{::uism/events
 {:event/save-ok
  {::uism/handler (fn [env] (uism/activate env :state/saved))
   ::uism/target-state :state/idle}

  :event/save-failed
  {::uism/handler (fn [env]
                   (-> env
                     (uism/assoc-aliased :error "Save failed")
                     (uism/activate :state/error)))}}}
```

### Triggering Remote Loads
```clojure
(defn load-handler [env]
  (-> env
    (uism/load :current-session :actor/current-session 
      {::uism/ok-event    :event/loaded
       ::uism/error-event :event/failed})
    (uism/activate :state/loading)))

;; Or reload an actor
(defn refresh-handler [env]
  (-> env
    (uism/load-actor :actor/current-account 
      {::uism/ok-event :event/refreshed})
    (uism/activate :state/refreshing)))
```

## Common Patterns

### Loading Pattern
```clojure
(defstatemachine loading-machine
  {::uism/actor-names #{:actor/data}
   
   ::uism/states
   {:initial
    {::uism/events
     {:event/load {::uism/handler load-handler}}}

    :state/loading
    {::uism/events
     {:event/loaded {::uism/target-state :state/ready}
      :event/failed {::uism/target-state :state/error}}}

    :state/ready
    {::uism/events
     {:event/refresh {::uism/handler load-handler}}}

    :state/error
    {::uism/events
     {:event/retry {::uism/handler load-handler}}}}})
```

### Form Editing Pattern
```clojure
(defstatemachine form-machine
  {::uism/actor-names #{:actor/form}
   
   ::uism/states
   {:state/viewing
    {::uism/events
     {:event/edit {::uism/handler start-edit-handler
                   ::uism/target-state :state/editing}}}

    :state/editing
    {::uism/events
     {:event/save   {::uism/handler save-handler}
      :event/cancel {::uism/handler cancel-handler
                     ::uism/target-state :state/viewing}}}

    :state/saving
    {::uism/events
     {:event/save-ok     {::uism/target-state :state/viewing}
      :event/save-failed {::uism/target-state :state/editing}}}}})
```

### Modal Dialog Pattern
```clojure
(defstatemachine modal-machine
  {::uism/actor-names #{:actor/modal}
   
   ::uism/states
   {:state/closed
    {::uism/events
     {:event/open {::uism/handler open-handler
                   ::uism/target-state :state/open}}}

    :state/open
    {::uism/events
     {:event/close  {::uism/target-state :state/closed}
      :event/submit {::uism/handler submit-handler
                     ::uism/target-state :state/submitting}}}

    :state/submitting
    {::uism/events
     {:event/submit-ok     {::uism/target-state :state/closed}
      :event/submit-failed {::uism/target-state :state/open}}}}})
```

## Machine Lifecycle

### Starting Machines
```clojure
;; From component lifecycle (e.g., componentDidMount)
(uism/begin! this form-machine ::form-sm
  {:actor/form [:item/id item-id]})

;; From mutation
(uism/begin! env login-machine ::login-sm
  {:actor/session (uism/with-actor-class [:session/id :main] Session)})

;; With singleton component classes
(uism/begin! this dialog-machine ::dialog-sm
  {:actor/dialog Dialog  ; Class with constant ident
   :actor/form   LoginForm})
```

### Stopping Machines
```clojure
;; From component lifecycle (e.g., componentWillUnmount)
(uism/exit! this ::form-sm)

;; From handler (machine exits itself)
(defn complete-handler [env]
  (uism/exit env))
```

## Debugging and Inspection

### Fulcro Inspect Integration
- **State visualization**: Current state and data visible in `::uism/asm-id` table
- **Event history**: See all events that occurred
- **Actor tracking**: Monitor component participation
- **Data inspection**: Examine machine state and aliases

### Querying Machine State from UI
```clojure
;; To use get-active-state, you must query for the machine's ident
(defsc Component [this props]
  {:query (fn [] [(uism/asm-ident ::my-machine) ...])}
  (let [current-state (uism/get-active-state this ::my-machine)]
    (dom/div
      (str "Current state: " current-state))))
```

### Logging Events
```clojure
;; Add to event handlers
(defn login-handler [env]
  (log/info "Login attempted for" (uism/actor->ident env :actor/current-session))
  (-> env ...))
```

## Best Practices

### Machine Design
- **Keep machines focused**: One concern per machine
- **Use descriptive state names**: `:state/loading` vs `:state-2`
- **Define clear events**: `:event/submit` vs `:event/do-thing`
- **Document transitions**: Comment complex logic

### Actor Management
- **Use stable idents**: Don't change actor identity unexpectedly
- **Minimize actors**: Only include what's needed
- **Clean up**: Exit machines when components unmount
- **Use `with-actor-class`**: When passing raw idents, wrap them with `(uism/with-actor-class ident Class)` for proper load/mutation support

### Event Handling
- **Pure handlers**: Avoid side effects outside of `uism/apply-action`
- **Use aliases**: Abstract machine data access for reusability
- **Handle errors**: Always plan for failure cases with error events
- **Keep handlers simple**: Extract complex logic into separate functions
- **Use `::uism/target-state` for simple transitions**: When an event only changes state, use the shorthand
- **Use event predicates sparingly**: For conditions that affect multiple events

### Integration with Components
- **Query machine state**: Include UISM idents in component queries if you use `get-active-state`
- **Use computed props**: Pass event triggers as callbacks
- **Conditional rendering**: Show UI based on machine state
- **Avoid direct manipulation**: Always go through machine events

### State Machine Reusability
- **Use derived machines**: State machine definitions are just maps - use `assoc-in`, `merge`, etc. to customize
- **Parameterize via local storage**: Use `::uism/started` event to store configuration in machine local storage
- **Leverage actors and aliases**: Keep machine logic decoupled from specific component structure
