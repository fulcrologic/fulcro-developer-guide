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
  {::uism/actors
   #{:current-session}

   ::uism/aliases
   {:logged-in? [::session :logged-in?]}

   ::uism/states
   {:initial
    {::uism/events
     {:event/check-session {:handler check-session-handler}}}

    :checking-session
    {::uism/events
     {:event/session-valid   {:target :logged-in}
      :event/session-invalid {:target :logged-out}}}

    :logged-in
    {::uism/events
     {:event/logout {:handler logout-handler :target :logged-out}}}

    :logged-out
    {::uism/events
     {:event/login {:handler login-handler}}}}})
```

### Key Elements
- **Actors**: Components that participate in the machine
- **Aliases**: Convenient paths to machine state
- **States**: Discrete phases of the workflow
- **Events**: Triggers that cause transitions
- **Handlers**: Functions that execute during transitions

## Working with Actors

### Actor Assignment
```clojure
;; Start machine with actors
(uism/begin! this session-machine ::session-sm
  {:current-session current-session-ident})

;; Actors are component idents
{:current-session [:session/id :main]}
```

### Actor Communication
```clojure
;; In event handler
(defn login-handler [env]
  (let [session-ident (uism/actor->ident env :current-session)]
    (-> env
      (uism/apply-action merge/merge-component! Session updated-session)
      (uism/activate :logged-in))))
```

## Event Handling

### Event Structure
```clojure
;; Basic event
{:event/login {:handler login-handler :target :logged-in}}

;; Event with guards
{:event/submit
 {:handler   submit-handler
  :guard     (fn [env] (uism/get-aliased-value env :form-valid?))
  :target    :submitting}}
```

### Event Handlers
```clojure
(defn login-handler [env]
  (-> env
    ;; Update application state
    (uism/apply-action merge/merge-component! Session {...})
    ;; Set machine data
    (uism/assoc-aliased :logged-in? true)
    ;; Trigger remote operation
    (uism/trigger-remote-mutation 'api/authenticate
      {:onOk   :event/login-ok
       :onErr  :event/login-failed})))
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
(uism/get-aliased-value env :current-step)
```

### Alias Configuration
```clojure
::uism/aliases
{:logged-in?    [::session :logged-in?]
 :current-user  [::session :current-user]
 :loading?      [::session :loading?]}
```

## Remote Operations

### Triggering Remote Calls
```clojure
(defn save-handler [env]
  (-> env
    (uism/activate :saving)
    (uism/trigger-remote-mutation 'api/save-item
      {:item-data (uism/get-aliased-value env :form-data)
       :onOk      :event/save-ok
       :onErr     :event/save-failed})))
```

### Handling Results
```clojure
:saving
{::uism/events
 {:event/save-ok
  {:handler (fn [env] (uism/activate env :saved))
   :target  :idle}

  :event/save-failed
  {:handler (fn [env]
             (-> env
               (uism/assoc-aliased :error "Save failed")
               (uism/activate :error)))}}}
```

## Common Patterns

### Loading Pattern
```clojure
(defstatemachine loading-machine
  {::uism/states
   {:initial
    {::uism/events
     {:event/load {:handler load-handler :target :loading}}}

    :loading
    {::uism/events
     {:event/loaded {:target :ready}
      :event/failed {:target :error}}}

    :ready
    {::uism/events
     {:event/refresh {:handler load-handler :target :loading}}}

    :error
    {::uism/events
     {:event/retry {:handler load-handler :target :loading}}}}})
```

### Form Editing Pattern
```clojure
(defstatemachine form-machine
  {::uism/states
   {:viewing
    {::uism/events
     {:event/edit {:handler start-edit-handler :target :editing}}}

    :editing
    {::uism/events
     {:event/save   {:handler save-handler}
      :event/cancel {:handler cancel-handler :target :viewing}}}

    :saving
    {::uism/events
     {:event/save-ok     {:target :viewing}
      :event/save-failed {:target :editing}}}}})
```

### Modal Dialog Pattern
```clojure
(defstatemachine modal-machine
  {::uism/states
   {:closed
    {::uism/events
     {:event/open {:handler open-handler :target :open}}}

    :open
    {::uism/events
     {:event/close  {:target :closed}
      :event/submit {:handler submit-handler :target :submitting}}}

    :submitting
    {::uism/events
     {:event/submit-ok     {:target :closed}
      :event/submit-failed {:target :open}}}}})
```

## Machine Lifecycle

### Starting Machines
```clojure
;; Component did mount
(uism/begin! this form-machine ::form-sm
  {:form-actor [:item/id item-id]})

;; From mutation
(uism/begin! env login-machine ::login-sm
  {:session [:session/id :main]})
```

### Stopping Machines
```clojure
;; Component will unmount
(uism/exit! this ::form-sm)

;; From handler
(uism/exit env)
```

## Debugging and Inspection

### Fulcro Inspect Integration
- **State visualization**: Current state and data visible
- **Event history**: See all events that occurred
- **Actor tracking**: Monitor component participation
- **Data inspection**: Examine machine state and aliases

### Logging Events
```clojure
;; Add to event handlers
(defn login-handler [env]
  (log/info "Login attempted for" (uism/actor->ident env :current-session))
  (-> env ...))
```

## Best Practices

### Machine Design
- **Keep machines focused**: One concern per machine
- **Use descriptive state names**: `:loading` vs `:state-2`
- **Define clear events**: `:event/submit` vs `:event/do-thing`
- **Document transitions**: Comment complex logic

### Actor Management
- **Use stable idents**: Don't change actor identity mid-flow
- **Minimize actors**: Only include what's needed
- **Clean up**: Exit machines when components unmount

### Event Handling
- **Pure handlers**: Avoid side effects outside of `apply-action`
- **Use aliases**: Abstract machine data access
- **Handle errors**: Always plan for failure cases
- **Keep handlers simple**: Complex logic in separate functions

### Integration with Components
- **Query machine state**: Include UISM queries in components
- **Use computed props**: Pass event triggers as callbacks
- **Conditional rendering**: Show UI based on machine state
- **Avoid direct manipulation**: Always go through machine events