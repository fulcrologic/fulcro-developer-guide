# Testing

## Overview
Fulcro provides comprehensive testing capabilities for components, mutations, state machines, and full-stack integration testing.

## Testing Philosophy

### Testable Architecture
Fulcro's architecture naturally supports testing:
- **Pure functions**: Render functions are pure
- **Immutable state**: Easy to set up test scenarios
- **Normalized database**: Predictable state structure
- **Separated concerns**: UI, logic, and networking are distinct

### Testing Levels
1. **Unit tests**: Individual functions and mutations
2. **Component tests**: UI component behavior
3. **Integration tests**: State machines and workflows
4. **End-to-end tests**: Full application scenarios

## Component Testing

### Basic Component Testing
```clojure
(ns app.ui-test
  (:require
    [cljs.test :refer [deftest is testing]]
    [com.fulcrologic.fulcro.components :as comp]
    [com.fulcrologic.fulcro.dom :as dom]
    [app.ui :as ui]))

(deftest person-component-test
  (testing "renders person data correctly"
    (let [props {:person/id 1 :person/name "John" :person/age 30}
          element (ui/ui-person props)]
      ;; Test element structure
      (is (= "div" (.-type element)))
      ;; Test that name appears in output
      (is (contains? (str element) "John")))))
```

### Testing with Fulcro Test Helpers
```clojure
(ns app.ui-test
  (:require
    [com.fulcrologic.fulcro.components :as comp]
    [com.fulcrologic.fulcro.dom :as dom]
    [fulcro-spec.core :refer [specification behavior component assertions]]))

(specification "Person Component"
  (let [person {:person/id 1 :person/name "John" :person/age 30}]
    (component ui/Person
      (behavior "displays person information"
        (assertions
          "shows the person's name"
          (comp/props (comp/factory ui/Person) person) => (contains {:person/name "John"})
          "shows the person's age"
          (comp/props (comp/factory ui/Person) person) => (contains {:person/age 30}))))))
```

### Testing with React Testing Library
```clojure
(ns app.ui-test
  (:require
    ["@testing-library/react" :as rtl]
    [com.fulcrologic.fulcro.dom :as dom]))

(deftest person-display-test
  (testing "Person component displays name and age"
    (let [props {:person/name "John" :person/age 30}
          container (rtl/render (ui/ui-person props))]
      (is (rtl/getByText container "John"))
      (is (rtl/getByText container "30")))))
```

## Mutation Testing

### Testing Mutation Logic
```clojure
(ns app.mutations-test
  (:require
    [cljs.test :refer [deftest is testing]]
    [com.fulcrologic.fulcro.mutations :as m]
    [app.mutations :as mut]))

(deftest update-person-test
  (testing "update-person mutation"
    (let [initial-state {:person/id {1 {:person/id 1 :person/name "John" :person/age 30}}}
          env {:state (atom initial-state)}]
      
      (testing "updates person name"
        ;; Execute the action portion of the mutation
        ((:action (m/mutate env 'mut/update-person {:person/id 1 :person/name "Jane"})) env)
        
        (let [updated-state @(:state env)]
          (is (= "Jane" (get-in updated-state [:person/id 1 :person/name])))
          (is (= 30 (get-in updated-state [:person/id 1 :person/age]))))))))
```

### Testing Mutation Remote Behavior
```clojure
(deftest update-person-remote-test
  (testing "update-person sends to remote"
    (let [env {:ast {:type :call :dispatch-key 'mut/update-person}
               :state (atom {})}
          remote-result ((:remote (m/mutate env 'mut/update-person {:person/id 1})) env)]
      
      (testing "returns AST for remote"
        (is (= true remote-result)) ; Simple true means send as-is
        ; or test modified AST
        ))))
```

## State Machine Testing

### Testing State Machine Logic
```clojure
(ns app.state-machines-test
  (:require
    [cljs.test :refer [deftest is testing]]
    [com.fulcrologic.fulcro.ui-state-machines :as uism]
    [app.state-machines :as sm]))

(deftest login-state-machine-test
  (testing "login state machine transitions"
    (let [initial-env (uism/new-asm sm/login-machine ::login-sm
                        {:session [:session/id :main]}
                        {})
          
          ;; Trigger login event
          env-after-login (uism/trigger initial-env :event/attempt-login
                            {:username "user" :password "pass"})]
      
      (testing "transitions to logging-in state"
        (is (= :logging-in (uism/get-active-state env-after-login))))
      
      (testing "stores login credentials"
        (is (= "user" (uism/get-aliased-value env-after-login :username)))))))
```

### Testing State Machine Handlers
```clojure
(defn test-login-handler []
  (let [env (uism/new-asm sm/login-machine ::login-sm
              {:session [:session/id :main]}
              {:username "test" :password "test123"})
        result-env (sm/attempt-login-handler env)]
    
    (testing "login handler behavior"
      (is (= :logging-in (uism/get-active-state result-env)))
      (is (uism/get-aliased-value result-env :loading?)))))
```

## Integration Testing

### Testing Data Loading
```clojure
(ns app.integration-test
  (:require
    [cljs.test :refer [deftest is testing async]]
    [com.fulcrologic.fulcro.application :as app]
    [com.fulcrologic.fulcro.data-fetch :as df]
    [app.client :as client]))

(deftest data-loading-test
  (async done
    (let [app (app/fulcro-app {:remotes {:remote (mock-remote)}})
          _ (app/mount! app client/Root "test-div")]
      
      (testing "loads people data"
        (df/load! app :people ui/Person
          {:post-action
           (fn [env]
             (let [state @(::app/state-atom app)
                   people (get state :people)]
               (is (seq people))
               (is (every? :person/id people))
               (done)))})))))
```

### Testing with Mock Server
```clojure
(defn mock-remote []
  (reify
    fr/FulcroRemoteI
    (transmit! [this {::ftx/keys [ast result-handler error-handler]}]
      (case (:dispatch-key ast)
        :people (result-handler {:transaction ast
                                 :body [{:person/id 1 :person/name "John"}
                                        {:person/id 2 :person/name "Jane"}]})
        :default (error-handler {:error "Not found"})))))
```

## Full Application Testing

### Setting Up Test Environment
```clojure
(ns app.test-utils
  (:require
    [com.fulcrologic.fulcro.application :as app]
    [com.fulcrologic.fulcro.components :as comp]))

(defn test-app
  "Create a test application with mock remotes"
  [& {:keys [initial-state remotes]}]
  (let [test-app (app/fulcro-app
                   {:remotes (or remotes {:remote (mock-remote)})
                    :initial-db (or initial-state {})})]
    test-app))

(defn with-test-app
  "Helper for running tests with a mounted app"
  [root-component test-fn]
  (let [app (test-app)
        container (js/document.createElement "div")]
    (js/document.body.appendChild container)
    (app/mount! app root-component container)
    (try
      (test-fn app)
      (finally
        (app/unmount! app)
        (js/document.body.removeChild container)))))
```

### End-to-End Workflow Testing
```clojure
(deftest complete-user-workflow-test
  (testing "complete user registration and login flow"
    (with-test-app ui/Root
      (fn [app]
        ;; Test registration
        (let [registration-data {:user/email "test@example.com"
                                 :user/password "password123"}]
          ;; Trigger registration
          (comp/transact! app [(api/register-user registration-data)])
          
          ;; Wait for completion and verify state
          (js/setTimeout
            (fn []
              (let [state @(::app/state-atom app)]
                (is (contains? state :current-user))
                (is (= "test@example.com" 
                       (get-in state [:current-user :user/email])))
                (done)))
            100))))))
```

## Performance Testing

### Render Performance Testing
```clojure
(deftest render-performance-test
  (testing "large list renders efficiently"
    (let [large-dataset (vec (for [i (range 1000)]
                               {:person/id i :person/name (str "Person " i)}))
          start-time (js/performance.now)]
      
      ;; Render large list
      (ui/ui-person-list {:people large-dataset})
      
      (let [render-time (- (js/performance.now) start-time)]
        (is (< render-time 100) "Render should complete within 100ms")))))
```

### Memory Leak Testing
```clojure
(deftest memory-leak-test
  (testing "components clean up properly"
    (let [initial-memory (when (exists? js/performance.memory)
                           (.-usedJSHeapSize js/performance.memory))]
      
      ;; Create and destroy many components
      (dotimes [i 100]
        (let [app (test-app)]
          (app/mount! app ui/Root "test-div")
          (app/unmount! app)))
      
      ;; Force garbage collection if available
      (when (exists? js/gc) (js/gc))
      
      (let [final-memory (when (exists? js/performance.memory)
                           (.-usedJSHeapSize js/performance.memory))
            memory-increase (when (and initial-memory final-memory)
                              (- final-memory initial-memory))]
        (when memory-increase
          (is (< memory-increase (* 10 1024 1024)) "Memory increase should be less than 10MB"))))))
```

## Testing Utilities

### Mock Data Helpers
```clojure
(ns app.test-data
  (:require [com.fulcrologic.fulcro.algorithms.tempid :as tempid]))

(defn mock-person
  [& {:keys [id name age] :or {id (tempid/tempid) name "Test Person" age 25}}]
  {:person/id id :person/name name :person/age age})

(defn mock-app-state
  [& {:keys [people current-user]}]
  (merge
    {:people (or people [(mock-person)])}
    (when current-user
      {:current-user current-user})))
```

### Assertion Helpers
```clojure
(defn contains-person? [state person-id]
  (contains? (:person/id state) person-id))

(defn has-validation-error? [form-state field]
  (boolean (get-in form-state [::fs/fields field ::fs/validation-message])))

(defn is-loading? [state marker]
  (get-in state [::df/markers marker ::df/loading?]))
```

## Test Organization

### Test Structure
```
test/
├── app/
│   ├── ui_test.cljs           # Component tests
│   ├── mutations_test.cljs    # Mutation tests
│   ├── state_machines_test.cljs # UISM tests
│   └── integration_test.cljs  # Integration tests
├── test_utils.cljs           # Test utilities
└── test_data.cljs           # Mock data helpers
```

### Test Configuration
```clojure
;; shadow-cljs.edn test configuration
{:builds
 {:test
  {:target    :node-test
   :output-to "target/test.js"
   :ns-regexp "-test$"}}}
```

## Best Practices

### Test Design
- **Test behavior, not implementation**: Focus on what components do
- **Use realistic data**: Test with production-like data
- **Test edge cases**: Empty states, error conditions, boundary values
- **Keep tests focused**: One concept per test

### Test Maintenance
- **DRY principles**: Extract common test setup
- **Clear test names**: Describe what is being tested
- **Fast feedback**: Keep tests quick to run
- **Deterministic tests**: Avoid flaky tests with timing issues

### Testing Strategy
- **Test pyramid**: More unit tests, fewer integration tests
- **Mock external dependencies**: Control test environment
- **Test critical paths**: Focus on important user workflows
- **Continuous testing**: Run tests on every change