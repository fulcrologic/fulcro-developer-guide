
# Advanced Fulcro Internals

## Overview

Fulcro 3 is designed to be easily modifiable and extensible. Understanding the internal architecture helps developers customize behavior, debug issues, and build advanced features. This chapter covers the core systems that power Fulcro applications.

## Transaction Processing System

### Core Concept

Most operations in Fulcro flow through the transaction processing system:

- Data loads (`df/load!`) create special transactions
- UI State Machines use `transact!` internally  
- Mutations are processed as transactions
- Network operations are scheduled through transactions

### Key Characteristics

**Fulcro does NOT watch the state atom** - rendering is triggered by transaction processing, not state changes. Direct `swap!` operations bypass Fulcro's mechanisms.

**Rendering is transaction-driven** - refreshes occur after:
- Optimistic mutation updates
- Network result merges
- Explicit render requests

### Default Algorithm Features

The built-in transaction processor (`tx_processing.cljc`) provides:

- **Optimistic execution**: Runs local mutations immediately (default behavior; use `{:optimistic? false}` for pessimistic)
- **Ordered network operations**: Requests process sequentially by default
- **Render scheduling**: Coordinates UI updates with render tasks
- **Result merging**: Integrates server responses into normalized database
- **Multi-remote splitting**: Distributes operations across remotes
- **Read/write combining**: Batches compatible operations

### Customizing Transaction Processing

The primary customization point for transaction processing is the `submit-transaction!` algorithm:

```clojure
(defn custom-tx-processor [app tx-data]
  ;; Custom processing logic
  (log/debug "Processing transaction:" tx-data)
  ;; Call default processor
  (txn/default-tx! app tx-data)
  ;; Additional post-processing
  (custom-analytics/track-transaction tx-data))

(def app
  (app/fulcro-app
    {:com.fulcrologic.fulcro.algorithm/tx! custom-tx-processor}))
```

### Safe Wrapper Pattern

```clojure
(defn instrumented-tx-processor [app tx-data]
  ;; Pre-process
  (when (development?)
    (validate-transaction tx-data))
  
  ;; Add timing
  (let [start-time (js/performance.now)]
    (try
      ;; Call original
      (txn/default-tx! app tx-data)
      (finally
        (log/debug "Transaction took:" 
                   (- (js/performance.now) start-time) "ms")))))
```

## Fulcro Application Structure

### Application as Data

A Fulcro application is an open map - you can add namespaced keys anywhere:

```clojure
(def app
  (app/fulcro-app
    {:my-company/analytics-client analytics-client
     :my-app/feature-flags feature-flags}))

;; Access anywhere
(defn get-analytics-client [app]
  (:my-company/analytics-client app))
```

### Core Application Keys

#### `::state-atom`
Contains the normalized client database:

```clojure
;; Read state (allowed)
(let [current-state @(::app/state-atom app)]
  (get-in current-state [:user/id 1]))

;; Modify state (advanced usage)
(swap! (::app/state-atom app) assoc-in [:ui/global :loading?] true)
(app/schedule-render! app)  ; Must manually trigger render
```

**Warning**: Direct state modification bypasses Fulcro's render system. Always call `schedule-render!` after direct modifications.

#### `::runtime-atom`
Tracks non-application data over time:

```clojure
;; Read runtime info
(let [runtime @(::app/runtime-atom app)]
  {:remotes (::app/remotes runtime)
   :indexes (::app/indexes runtime)
   :basis-t (::app/basis-t runtime)})

;; Add custom runtime data
(swap! (::app/runtime-atom app) 
       assoc :my-app/connection-status :connected)
```

**Use cases for runtime-atom**:
- Global configuration that changes over time
- Network state tracking
- Feature flags that update during runtime
- Plugin registrations (like RAD does)

#### `::indexes`
Component instance and class indexes:

```clojure
;; Find component instances by ident
(let [indexes (::app/indexes @(::app/runtime-atom app))
      components (get-in indexes [:ident->components [:user/id 1]])]
  (doseq [component components]
    (comp/set-state! component {:ui/highlighted? true})))
```

#### `::config`
Static configuration (no atom, immutable):

```clojure
(def app
  (app/fulcro-app
    {:my-app/api-endpoint "https://api.example.com"
     :my-app/feature-set :premium}))

;; Access static config
(defn get-api-endpoint [app]
  (get-in app [::app/config :my-app/api-endpoint]))
```

**Important**: The `::config` structure itself is **considered unstable and may change between Fulcro versions**. Always use your own namespaced keys for any configuration you want to preserve across version upgrades. Do not rely on the internal structure of `::config`.

#### `::basis-t`
Render time counter for targeted refresh:

```clojure
;; Current render time
(let [basis-t (::app/basis-t @(::app/runtime-atom app))]
  (log/debug "Current render generation:" basis-t))
```

This enables Fulcro's targeted refresh system where components receive newer props via `setState` rather than full tree re-renders. The counter increments with each render cycle.

## Algorithms Map

### Purpose

The algorithms map serves two functions:

1. **Dependency resolution**: Core systems can reference each other without circular dependencies
2. **User customization**: Override built-in behaviors

### Available Algorithms

Fulcro provides many customizable algorithms. Here are the most commonly overridden:

```clojure
(def app
  (app/fulcro-app
    {;; Custom error detection
     :com.fulcrologic.fulcro.algorithm/remote-error? 
     (fn [result] 
       (or (not= 200 (:status-code result))
           (contains? (:body result) :error)))
     
     ;; Custom render scheduling
     :com.fulcrologic.fulcro.algorithm/schedule-render! 
     (fn [app options]
       (js/setTimeout 
         #(default-render! app options) 16))
     
     ;; Custom state initialization
     :com.fulcrologic.fulcro.algorithm/initialize-state! 
     (fn [app root-class]
       (reset! (::app/state-atom app)
              (merge default-state custom-state)))
     
     ;; Custom global error handling
     :com.fulcrologic.fulcro.algorithm/global-error-action 
     (fn [env]
       (log/error "Global error:" (:error env))
       (show-error-dialog (:error env)))}))
```

### Other Available Algorithms

Additional customizable algorithms include:

- `:com.fulcrologic.fulcro.algorithm/tx!` - Main transaction processor
- `:com.fulcrologic.fulcro.algorithm/render!` - Rendering function
- `:com.fulcrologic.fulcro.algorithm/default-result-action!` - Network result handling
- `:com.fulcrologic.fulcro.algorithm/merge*` - State merging
- `:com.fulcrologic.fulcro.algorithm/render-middleware` - Middleware for render lifecycle
- `:com.fulcrologic.fulcro.algorithm/global-eql-transform` - Transform queries before sending

### Algorithm Access

```clojure
(defn get-algorithm [app key]
  (get-in app [::app/algorithms key]))

;; Example: Custom render
(defn force-render! [app]
  (let [render-fn (get-algorithm app :com.fulcrologic.fulcro.algorithm/render!)]
    (render-fn app {:force-root? true})))
```

## Auditing and History

### State Change Tracking

```clojure
(defn add-state-watcher! [app]
  (add-watch (::app/state-atom app) :audit
             (fn [key atom old-state new-state]
               (when (not= old-state new-state)
                 (record-state-change! old-state new-state)))))

(defn record-state-change! [old-state new-state]
  (let [timestamp (js/Date.)
        diff (data/diff old-state new-state)]
    (swap! audit-trail conj
           {:type :state-changed
            :timestamp timestamp
            :diff diff
            :before old-state
            :after new-state})))
```

### Transaction Auditing

```clojure
(defn audited-tx-processor [app tx-data]
  ;; Record transaction submission
  (swap! audit-trail conj
         {:type :transaction-submitted
          :timestamp (js/Date.)
          :tx tx-data})
  
  ;; Process normally
  (txn/default-tx! app tx-data))

(defn audited-result-action [env]
  ;; Record mutation results
  (swap! audit-trail conj
         {:type :remote-result
          :timestamp (js/Date.)
          :mutation (:ast env)
          :result (:result env)
          :error (:error env)})
  
  ;; Call default
  (mut/default-result-action! env))
```

### Load Tracking

```clojure
(defmulti audited-load-mutation (fn [env] (first (:ast env))))

(defmethod audited-load-mutation :default [env]
  ;; Record load
  (swap! audit-trail conj
         {:type :load-started
          :timestamp (js/Date.)
          :query (:ast env)})
  
  ;; Call default
  (df/internal-load env))

(def app
  (app/fulcro-app
    {:load-mutation `audited-load-mutation}))
```

### Audit Trail Structure

```clojure
(def audit-trail
  (atom
    [{:type :state-changed
      :timestamp #inst "2023-01-01T12:00:00"
      :before {...}
      :after {...}}
     {:type :transaction-submitted
      :timestamp #inst "2023-01-01T12:00:01"
      :tx [(save-user {:user/id 1})]}
     {:type :remote-result
      :timestamp #inst "2023-01-01T12:00:02"
      :mutation '(save-user {:user/id 1})
      :result {'save-user {:user/id 1 :user/name "Updated"}}}]))
```

## Time Travel Implementation

### Constraints and Limitations

1. **Fulcro db only**: Component-local state is not tracked
2. **Serialization requirements**: All data must survive serialize/deserialize
3. **UI-only time travel**: Not full distributed system time travel
4. **Development/debugging focus**: Not a production feature

### Basic Time Travel

```clojure
(defn reset-app-state!
  "Time travel to a specific app state"
  [app new-state]
  (reset! (::app/state-atom app) new-state)
  (app/schedule-render! app {:force-root? true}))

(defn get-app-state
  "Capture current app state"
  [app]
  @(::app/state-atom app))

;; Usage
(def saved-state (get-app-state app))
;; ... user interactions ...
(reset-app-state! app saved-state)  ; Go back
```

### Advanced: Non-destructive State Viewing

```clojure
(defn show-state!
  "Display alternate state without affecting app operation"
  [app state-map]
  (binding [comp/*blindly-render* true]
    (let [{::app/keys [runtime-atom]} app
          {::app/keys [root-factory root-class mount-node]} @runtime-atom
          render-fn (get-algorithm app :com.fulcrologic.fulcro.algorithm/render-root!)
          query (comp/get-query root-class state-map)
          data-tree (if query
                      (fdn/db->tree query state-map state-map)
                      state-map)]
      (render-fn (root-factory data-tree) mount-node))))

;; Show historical state temporarily
(show-state! app historical-state)
;; Next normal render will return to present
```

### Support Viewer Implementation

#### 1. Dual-Mount Setup

```html
<div id="support-viewer"></div>
<div id="app-viewer"></div>
```

#### 2. Support Viewer App

```clojure
(defsc SupportViewer [this {:keys [crash-reports selected-report]}]
  {:query [:crash-reports {:selected-report [:report/id :report/audit-trail]}]}
  (dom/div
    (dom/h2 "Crash Reports")
    (dom/ul
      (map (fn [report]
             (dom/li {:key (:report/id report)
                     :onClick #(select-report! this report)}
                    (:report/title report)))
           crash-reports))
    
    (when selected-report
      (time-travel-controls this selected-report))))

(def support-app
  (app/fulcro-app {}))

(app/mount! support-app SupportViewer "support-viewer")
```

#### 3. Neutered Production App

```clojure
(defn create-replay-app [crash-report]
  (app/fulcro-app
    {;; No remotes
     :remotes {}
     
     ;; No-op transaction processing
     :com.fulcrologic.fulcro.algorithm/tx! (fn [app tx] 
                                             (log/debug "Replay app ignoring tx:" tx))
     
     ;; No-op loads
     :load-mutation (fn [env] 
                     (log/debug "Replay app ignoring load"))
     
     ;; Initial state from crash report
     :initial-db (:initial-state crash-report)}))

(defn replay-crash [crash-report]
  (let [replay-app (create-replay-app crash-report)]
    (app/mount! replay-app ProductionRoot "app-viewer")
    replay-app))
```

#### 4. Time Travel Controls

```clojure
(defsc TimeTravel [this {:keys [current-step max-steps audit-trail]}]
  (dom/div
    (dom/input {:type "range"
               :min 0
               :max max-steps
               :value current-step
               :onChange #(jump-to-step! this (.. % -target -value))})
    (dom/div (str "Step " current-step " of " max-steps))
    (dom/button {:onClick #(step-backward! this)} "←")
    (dom/button {:onClick #(step-forward! this)} "→")
    (dom/button {:onClick #(jump-to-beginning! this)} "⏮")
    (dom/button {:onClick #(jump-to-end! this)} "⏭")))

(defmutation jump-to-step [{:keys [step]}]
  (action [{:keys [state]}]
    (let [audit-trail (get @state :audit-trail)
          target-state (:after (nth audit-trail step))]
      (swap! state assoc :current-step step)
      (show-state! replay-app target-state))))
```

## Performance Monitoring

### Render Performance

```clojure
(defn timed-render! [app options]
  (let [start (js/performance.now)
        default-render (get-algorithm app :com.fulcrologic.fulcro.algorithm/render!)]
    (default-render app options)
    (let [duration (- (js/performance.now) start)]
      (when (> duration 16)  ; Warn if render takes > 1 frame
        (log/warn "Slow render detected:" duration "ms")))))

(def app
  (app/fulcro-app
    {:com.fulcrologic.fulcro.algorithm/render! timed-render!}))
```

### Transaction Performance

```clojure
(defn performance-tracked-tx [app tx-data]
  (let [start (js/performance.now)
        tx-id (random-uuid)]
    (log/debug "Starting transaction:" tx-id)
    
    ;; Wrap result handlers to track completion time
    (let [instrumented-tx (update tx-data :result-handler
                                 (fn [original-handler]
                                   (fn [result]
                                     (let [duration (- (js/performance.now) start)]
                                       (log/debug "Transaction completed:" tx-id duration "ms")
                                       (when original-handler
                                         (original-handler result))))))]
      (txn/default-tx! app instrumented-tx))))
```

## Best Practices for Advanced Usage

### 1. Namespace Your Extensions

```clojure
;; Good: Namespaced keys
{:my-company/feature-flags {...}
 :my-app/analytics {...}}

;; Bad: Unnamespaced keys (may conflict)
{:feature-flags {...}
 :analytics {...}}
```

### 2. Preserve Default Behavior

```clojure
;; Good: Wrapper pattern
(defn custom-algorithm [app & args]
  (pre-process args)
  (let [result (apply default-algorithm app args)]
    (post-process result)
    result))

;; Risky: Complete replacement
(defn custom-algorithm [app & args]
  (completely-different-implementation))
```

### 3. Development vs Production

```clojure
(def app
  (app/fulcro-app
    (cond-> base-config
      development? (assoc :com.fulcrologic.fulcro.algorithm/submit-transaction! audited-tx-processor)
      production?  (assoc :error-reporting error-service))))
```

### 4. Safe Runtime Modifications

```clojure
;; Safe: Read runtime data
(defn get-network-status [app]
  (get-in @(::app/runtime-atom app) [:my-app/network-status]))

;; Safe: Add runtime data
(defn set-network-status! [app status]
  (swap! (::app/runtime-atom app) 
         assoc-in [:my-app/network-status] status))

;; Dangerous: Modifying Fulcro's runtime data
(defn dangerous-modification [app]
  (swap! (::app/runtime-atom app)
         assoc ::app/remotes {}))  ; Don't do this!
```

Understanding Fulcro's internals enables powerful customizations while maintaining the framework's core guarantees around data flow, rendering, and state management.
