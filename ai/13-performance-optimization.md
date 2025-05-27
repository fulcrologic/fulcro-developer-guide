# Performance Optimization

## Overview
Fulcro provides multiple strategies for optimizing application performance, from rendering optimizations to data loading patterns and bundle size management.

## Rendering Optimization

### Ident-Optimized Rendering
Fulcro can skip rendering parent components when only child data changes:

```clojure
;; Enable ident-optimized rendering (default in newer versions)
(app/fulcro-app
  {:optimized-render! ident-optimized-render/render!})

;; Component with proper ident for optimization
(defsc Person [this {:person/keys [id name age]}]
  {:query [:person/id :person/name :person/age]
   :ident :person/id}  ; Required for optimization
  (dom/div name " (" age ")"))
```

### React Optimization Features
```clojure
;; Use React.memo for functional components
(def ui-person (comp/factory (comp/memo Person) {:keyfn :person/id}))

;; Use shouldComponentUpdate for class components
(defsc ExpensiveComponent [this props]
  {:shouldComponentUpdate (fn [this next-props next-state]
                            ;; Custom comparison logic
                            (not= (select-keys props [:critical/field])
                                  (select-keys next-props [:critical/field])))}
  ;; Expensive rendering logic
  )
```

### Render Middleware for Profiling
```clojure
(app/fulcro-app
  {:render-middleware
   (fn [this real-render]
     ;; Profile rendering time
     (let [start (js/Date.now)]
       (try
         (real-render)
         (finally
           (let [duration (- (js/Date.now) start)]
             (when (> duration 16) ; Log slow renders
               (log/warn "Slow render:" (comp/component-name this) duration "ms")))))))})
```

## Query Optimization

### Keep Queries Minimal
```clojure
;; Good: Only query what you need
(defsc PersonCard [this {:person/keys [id name avatar]}]
  {:query [:person/id :person/name :person/avatar]}
  (dom/div
    (dom/img {:src avatar})
    (dom/span name)))

;; Avoid: Querying unnecessary data
(defsc PersonCard [this person]
  {:query [:person/id :person/name :person/avatar
           :person/address :person/phone :person/email]} ; Unused fields
  (dom/div
    (dom/img {:src (:person/avatar person)})
    (dom/span (:person/name person))))
```

### Dynamic Query Optimization
```clojure
;; Use focused queries for specific operations
(df/load! this :people Person 
  {:focus [:person/id :person/name]}) ; Only load essential fields

;; Load additional data on demand
(defsc PersonDetail [this {:person/keys [id name email] :as props}]
  {:componentDidMount
   (fn [this]
     (when (and id (not email)) ; Load details if not present
       (df/load! this [:person/id id] PersonDetail
         {:focus [:person/email :person/phone :person/address]})))}
  ...)
```

## Data Loading Optimization

### Parallel Loading
```clojure
;; Load multiple resources in parallel
(defn load-dashboard-data [this]
  (df/load! this :recent-posts Post {:parallel true})
  (df/load! this :user-stats Stats {:parallel true})
  (df/load! this :notifications Notification {:parallel true}))

;; Use load-multiple for related data
(df/load-multiple! this
  [[:recent-posts Post]
   [:user-stats Stats]
   [:notifications Notification]])
```

### Incremental Loading
```clojure
;; Load critical data first, then load supplementary data
(defsc Dashboard [this props]
  {:componentDidMount
   (fn [this]
     ;; Critical data first
     (df/load! this :user-profile UserProfile
       {:post-action (fn [env]
                       ;; Load supplementary data after profile loads
                       (df/load! this :user-preferences Preferences)
                       (df/load! this :recent-activity Activity))}))}
  ...)
```

### Caching Strategies
```clojure
;; Avoid reloading unchanged data
(defsc PersonList [this {:keys [people last-updated]}]
  {:componentDidMount
   (fn [this]
     (let [cache-time (* 5 60 1000) ; 5 minutes
           now (js/Date.now)]
       (when (or (not last-updated)
                 (> (- now last-updated) cache-time))
         (df/load! this :people Person
           {:post-mutation `update-timestamp}))))}
  ...)
```

## Memory Management

### Component Cleanup
```clojure
(defsc TimerComponent [this props]
  {:componentDidMount
   (fn [this]
     (let [timer (js/setInterval #(update-timer this) 1000)]
       (comp/set-state! this {:timer timer})))
   
   :componentWillUnmount
   (fn [this]
     (when-let [timer (:timer (comp/get-state this))]
       (js/clearInterval timer)))}
  ...)
```

### Event Listener Management
```clojure
(defsc WindowResizeComponent [this props]
  {:componentDidMount
   (fn [this]
     (let [handler #(handle-resize this %)]
       (comp/set-state! this {:resize-handler handler})
       (js/window.addEventListener "resize" handler)))
   
   :componentWillUnmount
   (fn [this]
     (when-let [handler (:resize-handler (comp/get-state this))]
       (js/window.removeEventListener "resize" handler)))}
  ...)
```

### Large List Handling
```clojure
;; Use virtualization for large lists
(defsc VirtualizedList [this {:keys [items]}]
  {:componentDidMount
   (fn [this]
     ;; Only render visible items
     (comp/set-state! this {:visible-start 0 :visible-count 20}))}
  (let [{:keys [visible-start visible-count]} (comp/get-state this)
        visible-items (subvec items visible-start (+ visible-start visible-count))]
    (dom/div
      (map ui-list-item visible-items))))
```

## Bundle Size Optimization

### Code Splitting
```clojure
;; Shadow-cljs configuration for code splitting
{:builds
 {:main
  {:modules
   {:main     {:init-fn app.client/init}
    :admin    {:depends-on #{:main}
               :entries [app.admin]}
    :reports  {:depends-on #{:main}
               :entries [app.reports]}}}}}

;; Dynamic loading in router
(defsc AdminPage [this props]
  {:route-segment ["admin"]
   :will-enter    (fn [app params]
                    ;; Load admin module on demand
                    (-> (js/import "./admin.js")
                        (.then #(dr/route-immediate [:component/id ::AdminPage]))))}
  ...)
```

### Tree Shaking
```clojure
;; Good: Import only what you need
(ns app.ui
  (:require [com.fulcrologic.fulcro.dom :as dom :refer [div span]]))

;; Avoid: Importing entire namespaces unnecessarily
(ns app.ui
  (:require [some.large.library :as lib])) ; Imports everything
```

### Dependency Optimization
```clojure
;; Use lighter alternatives when possible
;; Instead of moment.js (large), use date-fns (smaller, tree-shakeable)
(ns app.utils.date
  (:require ["date-fns/format" :as date-format]
            ["date-fns/parseISO" :as parse-iso]))

;; Avoid heavy libraries for simple tasks
;; Instead of lodash for simple operations, use native JS/Clojure
```

## Database Optimization

### Efficient Mutations
```clojure
;; Good: Update specific entity
(defmutation update-person-name
  [{:keys [person-id new-name]}]
  (action [{:keys [state]}]
    (swap! state assoc-in [:person/id person-id :person/name] new-name)))

;; Avoid: Updating entire collections
(defmutation update-person-name-slow
  [{:keys [person-id new-name]}]
  (action [{:keys [state]}]
    (swap! state update :people
      (fn [people]
        (mapv #(if (= (:person/id %) person-id)
                 (assoc % :person/name new-name)
                 %)
              people)))))
```

### Batch Updates
```clojure
;; Batch multiple updates in single transaction
(defmutation batch-update-people
  [{:keys [updates]}]
  (action [{:keys [state]}]
    (swap! state
      (fn [s]
        (reduce (fn [state [person-id field value]]
                  (assoc-in state [:person/id person-id field] value))
                s
                updates)))))
```

### Normalized Data Benefits
```clojure
;; Good: Normalized reference (O(1) lookup)
{:current-user [:person/id 123]
 :person/id {123 {:person/id 123 :person/name "John"}}}

;; Avoid: Denormalized duplication (O(n) updates)
{:current-user {:person/id 123 :person/name "John"}
 :recent-posts [{:author {:person/id 123 :person/name "John"}}]}
```

## Network Optimization

### Request Batching
```clojure
;; Enable request batching (default in Fulcro)
(app/fulcro-app
  {:remotes {:remote (http/fulcro-http-remote
                       {:batch-requests? true
                        :batch-timeout   100})}}) ; Batch requests within 100ms
```

### Compression
```clojure
;; Server-side gzip compression
(def middleware
  (-> handler
    (wrap-gzip)
    (wrap-api {...})))

;; Client-side request compression
(http/fulcro-http-remote
  {:request-middleware [(fn [req] (assoc req :compress-request? true))]})
```

### Caching Headers
```clojure
;; Server response caching
(defn api-handler [req]
  {:status 200
   :headers {"Cache-Control" "public, max-age=300"} ; 5 minute cache
   :body response-data})
```

## Measurement and Monitoring

### Performance Monitoring
```clojure
(defn measure-render-time []
  (let [start (js/performance.now)]
    (app/schedule-render! app)
    (js/requestAnimationFrame
      #(log/info "Render time:" (- (js/performance.now) start) "ms"))))
```

### Memory Usage Tracking
```clojure
(defn log-memory-usage []
  (when (exists? js/performance.memory)
    (let [memory js/performance.memory]
      (log/info "Memory usage:"
                "Used:" (/ (.-usedJSHeapSize memory) 1024 1024) "MB"
                "Total:" (/ (.-totalJSHeapSize memory) 1024 1024) "MB"))))
```

### Query Performance Analysis
```clojure
;; Add timing to load operations
(df/load! this :people Person
  {:marker :people-loading
   :post-action (fn [env]
                  (let [duration (- (js/Date.now) load-start)]
                    (log/info "People loaded in" duration "ms")))})
```

## Best Practices

### General Guidelines
- **Measure first**: Profile before optimizing
- **Optimize bottlenecks**: Focus on actual performance problems
- **Progressive optimization**: Start with biggest wins
- **Monitor continuously**: Performance can regress over time

### Component Design
- **Pure components**: Avoid side effects in render
- **Stable keys**: Use consistent React keys for list items
- **Minimal props**: Pass only necessary data to components
- **Memoization**: Cache expensive calculations

### Data Patterns
- **Normalize data**: Use graph database efficiently
- **Batch operations**: Group related updates
- **Lazy loading**: Load data when needed
- **Cache strategically**: Balance memory vs network

### Development Workflow
- **Production builds**: Test performance with optimized builds
- **Realistic data**: Use production-like data volumes
- **Device testing**: Test on target devices/networks
- **Continuous monitoring**: Track performance metrics over time