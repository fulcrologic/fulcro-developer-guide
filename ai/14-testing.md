
# Testing

## Overview

Fulcro provides comprehensive testing capabilities for components, mutations, state machines, and full-stack integration testing. The architecture naturally supports testing because state, logic, and UI are separated.

## Testing Philosophy

### Why Fulcro Code is Testable

Fulcro's architecture naturally supports testing:
- **Pure functions**: Components are pure functions of their props
- **Immutable state**: Easy to set up and inspect test scenarios
- **Normalized database**: Predictable and introspectable state structure
- **Separated concerns**: Business logic, state mutations, and UI rendering are distinct
- **State-driven**: All application behavior ultimately boils down to state mutations

### Testing Pyramid

The recommended testing approach follows a test pyramid:
1. **Unit tests**: Test individual mutations and state transitions (fast, deterministic)
2. **Integration tests**: Test state machine behavior and multi-step workflows
3. **Component/UI tests**: Test UI rendering and interaction (fewer, slower)

Most of your tests should be at the unit/integration level, testing the state model and business logic without requiring UI rendering.

## Unit Testing: Mutations and State

### Testing Mutation Actions

Mutations are tested by directly calling their action handlers and verifying state changes:

```clojure
(ns app.mutations-test
  (:require
    [cljs.test :refer [deftest is testing]]
    [app.mutations :as mut]))

(deftest update-person-test
  (testing "update-person mutation"
    (let [initial-state {:person/id {1 {:person/id 1 :person/name "John" :person/age 30}}}
          state-atom (atom initial-state)]
      
      (testing "updates person name in state"
        ;; Simulate what the mutation's action block would do
        (swap! state-atom assoc-in [:person/id 1 :person/name] "Jane")
        
        (let [updated-state @state-atom]
          (is (= "Jane" (get-in updated-state [:person/id 1 :person/name])))
          (is (= 30 (get-in updated-state [:person/id 1 :person/age]))))))))
```

### Testing Form State Mutations

Form mutations are typically tested using Fulcro's form-state utilities:

```clojure
(ns app.forms-test
  (:require
    [cljs.test :refer [deftest is testing]]
    [com.fulcrologic.fulcro.components :as comp]
    [com.fulcrologic.fulcro.form-state :as fs]
    [app.mutations :as mut]))

(deftest form-submission-test
  (testing "form submission"
    (let [form-state {:phone/id 1
                      :phone/number "555-1234"
                      :phone/type "mobile"
                      fs/form-config-join {}}
          
          ;; Mark field as dirty after user input
          form-with-changes (assoc form-state 
                               ::fs/fields {:phone/number {::fs/dirty? true}})
          
          ;; Check form state utilities
          dirty? (fs/dirty? form-with-changes)
          validity (fs/get-spec-validity form-with-changes)]
      
      (is dirty? "Form should be dirty after changes")
      (is (some? validity) "Should have validity information"))))
```

## Component Testing

### Testing Component Queries and Initial State

Components are tested by verifying their declared requirements (queries, idents, initial-state):

```clojure
(ns app.components-test
  (:require
    [cljs.test :refer [deftest is testing]]
    [com.fulcrologic.fulcro.components :as comp]
    [app.ui :as ui]))

(deftest person-component-test
  (testing "Person component"
    ;; Test that the component has required properties
    (let [person-props {:person/id 1 :person/name "John" :person/age 30}
          component-ident (comp/get-ident ui/Person person-props)]
      
      (is (some? component-ident) "Component should have an ident")
      (is (= [:person/id 1] component-ident) "Ident should match expected format"))
    
    ;; Test initial state composition
    (let [initial (comp/get-initial-state ui/Root {})]
      (is (map? initial) "Should return a valid initial state")
      (is (contains? initial :root/people) "Should have required root keys"))))
```

### Testing Component Behavior with State Machines

For components that manage complex behavior, test via state transitions:

```clojure
(ns app.countdown-test
  (:require
    [cljs.test :refer [deftest is testing]]
    [com.fulcrologic.fulcro.application :as app]
    [com.fulcrologic.fulcro.components :as comp]
    [app.ui :as ui]
    [app.state-machines :as sm]))

(deftest countdown-component-test
  (testing "countdown component behavior"
    ;; Use a headless synchronous app for component testing
    (let [test-app (app/headless-synchronous-app ui/Root {})
          initial-state (app/current-state test-app)
          countdown-count (get-in initial-state [:ui/id :ui/countdown :ui/count])]
      
      (is (some? countdown-count) "Should have countdown initialized")
      (is (> countdown-count 0) "Countdown should have positive value"))))
```

<!-- TODO: Verify this claim - React Testing Library integration for UI verification -->

## State Machine (UISM) Testing

### Testing State Machine Transitions

State machines are tested using the headless synchronous app pattern:

```clojure
(ns app.state-machines-test
  (:require
    [cljs.test :refer [deftest is testing]]
    [com.fulcrologic.fulcro.application :as app]
    [com.fulcrologic.fulcro.ui-state-machines :as uism]
    [app.state-machines :as sm]
    [app.ui :as ui]))

(deftest login-state-machine-test
  (testing "login state machine transitions"
    (let [app (app/headless-synchronous-app ui/Root {})
          ;; Begin the state machine
          _ (uism/begin! app sm/login-machine ::login-sm
              {:actor/login-form ui/LoginForm}
              {})
          
          ;; Verify initial state
          initial-state (uism/get-active-state app ::login-sm)]
      
      (is (some? initial-state) "Should have initialized state machine")
      
      ;; Trigger an event
      (uism/trigger! app ::login-sm :event/attempt-login
        {:username "user@example.com" :password "pass123"})
      
      ;; Verify state changed
      (let [after-event-state (uism/get-active-state app ::login-sm)]
        (is (= :state/logging-in after-event-state) 
            "Should transition to logging-in state"))
      
      ;; Check state machine values
      (let [state-tree (app/current-state app)]
        (is (some? (get-in state-tree [::uism/asm-id ::login-sm]))
            "Should have state machine in app state")))))
```

### Advanced State Machine Testing

For state machines with complex behavior, test specific event handlers:

```clojure
(deftest login-success-test
  (testing "successful login flow"
    (let [app (app/headless-synchronous-app ui/Root {})
          _ (uism/begin! app sm/login-machine ::login-sm
              {:actor/login-form ui/LoginForm}
              {})]
      
      ;; Simulate successful login sequence
      (uism/trigger! app ::login-sm :event/attempt-login
        {:username "test@example.com" :password "test123"})
      
      ;; Wait for async completion (in real tests, mock the server)
      (uism/trigger! app ::login-sm :event/login-success
        {:account/id 42 :account/name "Test User"})
      
      ;; Verify final state
      (let [state (app/current-state app)
            active-state (uism/get-active-state app ::login-sm)]
        (is (= :state/logged-in active-state)
            "Should be in logged-in state")
        (is (= 42 (get-in state [:account/id 42 :account/id]))
            "Should have stored account data")))))
```

## Integration Testing

### Setting Up a Headless Test Application

The recommended approach for integration testing is using a headless synchronous application:

```clojure
(ns app.integration-test
  (:require
    [cljs.test :refer [deftest is testing async]]
    [com.fulcrologic.fulcro.application :as app]
    [com.fulcrologic.fulcro.components :as comp]
    [com.fulcrologic.fulcro.ui-state-machines :as uism]
    [app.client :as client]
    [app.ui :as ui]
    [app.state-machines :as sm]))

(defn make-test-app []
  "Create a test application with no remotes"
  (app/headless-synchronous-app client/Root {}))

(deftest integration-workflow-test
  (testing "complete workflow: login → load data → display"
    (let [test-app (make-test-app)]
      
      ;; Start a state machine
      (uism/begin! test-app sm/login-machine ::login-sm
        {:actor/login-form ui/LoginForm}
        {})
      
      ;; Verify it started
      (is (= :state/initial (uism/get-active-state test-app ::login-sm)))
      
      ;; Trigger events
      (uism/trigger! test-app ::login-sm :event/login
        {:email "user@example.com" :password "password"})
      
      ;; After login, verify state
      (let [state (app/current-state test-app)]
        (is (contains? state :current-user) "Should have user logged in")
        (is (contains? state :people) "Should have loaded people data")))))
```

### Testing Data Loading

Data loading is tested by verifying state updates after loads complete:

```clojure
(deftest data-loading-test
  (testing "loading people data"
    (let [app (make-test-app)
          ;; Initial state should not have people
          initial-state (app/current-state app)]
      
      (is (empty? (get initial-state :people)) 
          "Should start with empty people")
      
      ;; Load people (with mock server, this would complete synchronously)
      ;; This is pseudo-code; actual implementation depends on your server setup
      (comp/transact! app 
        [(list 'app.mutations/load-people {})])
      
      ;; Check that state was updated
      (let [final-state (app/current-state app)]
        (is (seq (get final-state :people))
            "Should have people after load")))))
```

## Testing with Mocked Server Interactions

For testing with server interactions, you can create a custom mock remote:

```clojure
(ns app.server-test
  (:require
    [cljs.test :refer [deftest is testing]]
    [com.fulcrologic.fulcro.application :as app]
    [com.fulcrologic.fulcro.algorithms.tx-processing :as txn]))

;; Create a mock remote that returns predefined responses
(defn mock-remote [response-map]
  (reify txn/FulcroRemoteI
    (transmit! [this {::txn/keys [ast result-handler]}]
      ;; Return mocked data based on the query
      (let [query (:query ast)
            response (get response-map query)]
        (result-handler (or response {}))))))

(deftest mock-remote-test
  (testing "app can use mocked remote responses"
    (let [mock-responses {[:person/id 1] {:person/id 1 :person/name "John"}}
          app (app/fulcro-app {:remotes {:remote (mock-remote mock-responses)}})]
      ;; Trigger loads and verify state using app's state atom
      ;; @(::app/state-atom app)
      )))
```

**Note**: For comprehensive server testing with Pathom resolvers, test your resolvers directly using Pathom's testing utilities on the server side, then use simple mocked responses in client tests.

## Testing Best Practices

### Design Principles

**Test behavior, not implementation:**
- Test that state changes correctly, not the specific code that changes it
- Test that mutations produce expected side effects
- Test that state machines transition correctly

**Use predictable test data:**
- Create helper functions for generating test data
- Use the same test data across multiple tests
- Avoid randomness (unless testing randomness)

**Test edge cases and error conditions:**
- Empty states, null values, missing data
- Error responses from server
- Invalid user input
- Boundary conditions

### Testing Structure

```clojure
;; Well-structured test with clear phases
(deftest meaningful-test-name
  (testing "user can update their profile"
    ;; SETUP: Create the test environment
    (let [test-app (make-test-app)
          user {:user/id 1 :user/name "John"}]
      
      ;; EXECUTE: Perform the action
      (comp/transact! test-app
        [(mutations/update-user user)])
      
      ;; VERIFY: Check the result
      (let [state (app/current-state test-app)
            updated-user (get-in state [:user/id 1])]
        (is (= user updated-user)
            "User data should be updated in state")))))
```

### Keeping Tests Fast and Focused

- **One concept per test**: Test one behavior or state transition
- **Fast execution**: Headless apps run synchronously and complete instantly
- **No external dependencies**: Mock all server interactions
- **Clear test names**: Describe what behavior is being tested
- **DRY test code**: Extract common setup into helper functions

## Testing State and Mutations

### Example: Testing State Updates

```clojure
(ns app.state-mutations-test
  (:require
    [cljs.test :refer [deftest is testing]]
    [app.mutations :as mut]))

(deftest increment-counter-test
  (testing "incrementing a counter in state"
    (let [state {:counter 0}
          new-state (-> state
                      (assoc :counter (inc (:counter state))))]
      (is (= 1 (:counter new-state)) "Counter should increment"))))

(deftest list-operations-test
  (testing "adding items to a list"
    (let [state {:items [1 2 3]}
          new-state (update state :items conj 4)]
      (is (= [1 2 3 4] (:items new-state))
          "Should append new item to list"))))
```

## Debugging Tests

### Inspecting Application State

```clojure
;; Print the entire app state for debugging
(defn debug-state [app]
  (js/console.log (clj->js (app/current-state app))))

;; Use this in tests to inspect state at breakpoints
(deftest debug-test
  (let [app (make-test-app)]
    ;; ... run some transactions ...
    (debug-state app)  ;; prints state to console
    ;; ... assert things ...
    ))
```

### Using Browser DevTools

When testing in the browser:
- Fulcro DevTools browser extension shows application state in real-time
- Check the application state structure and trace mutations
- Verify component queries match returned data
- Step through state machine transitions

## Test File Organization

A typical test organization looks like:

```
test/
├── app/
│   ├── mutations_test.cljs      # Unit tests for mutations
│   ├── state_test.cljs          # State computation tests
│   ├── components_test.cljs     # Component query/ident tests
│   ├── state_machines_test.cljs # UISM behavior tests
│   └── integration_test.cljs    # Full workflow tests
├── test_utils.cljs             # Shared test helpers
└── test_data.cljs              # Test data factories
```

## Summary

Effective testing in Fulcro focuses on:

1. **Unit test mutations and state transitions** - Fast, deterministic, no UI
2. **Integration test workflows** - Using headless synchronous apps
3. **Test state machines separately** - Using UISM APIs with defined state machines
4. **Keep UI testing minimal** - Only test complex UI interactions when necessary
5. **Use test helpers** - Extract common patterns into reusable functions

The key insight is that Fulcro's architecture enables testing business logic independent of UI rendering, making tests faster and more reliable.
