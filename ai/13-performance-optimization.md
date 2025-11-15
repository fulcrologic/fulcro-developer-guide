
# Performance Optimization

## Overview
Fulcro provides multiple strategies for optimizing application performance, from rendering optimizations to data loading patterns and bundle size management.

## Rendering Optimization

### Ident-Optimized Rendering
Fulcro can skip rendering parent components when only child data changes. By using ident-optimized rendering (available and recommended in newer versions), Fulcro can target updates to specific components instead of re-rendering from the root:

```clojure
;; Enable ident-optimized rendering
(app/fulcro-app
  {:optimized-render! (require [com.fulcrologic.fulcro.rendering.ident-optimized-render :refer [render!]])
   render!})

;; Component with proper ident for optimization
(defsc Person [this {:person/keys [id name age]}]
  {:query [:person/id :person/name :person/age]
   :ident :person/id}  ; Required for optimization to target this component
  (dom/div name " (" age ")"))
```

When a component has an `:ident`, Fulcro's rendering system can update that component directly by querying its data from the database, skipping parent re-renders.

### Fulcro's Built-in Rendering Optimization
Fulcro's components automatically benefit from `shouldComponentUpdate` optimization when using `defsc`. You can customize this behavior if needed:

```clojure
;; defsc provides automatic shouldComponentUpdate
(defsc ExpensiveComponent [this {:keys [critical-field]}]
  {:query [:critical-field :other-field]
   ;; Customize comparison if needed
   :shouldComponentUpdate (fn [this next-props next-state]
                            ;; Return false to skip rendering
                            (= (select-keys (comp/props this) [:critical-field])
                               (select-keys next-props [:critical-field])))}
  ;; Rendering logic
  (dom/div critical-field))
```

Note: React.memo is not recommended for use with Fulcro defsc components, as Fulcro's component system has its own optimization mechanisms.

### Render Middleware for Profiling
You can use render middleware to measure and profile rendering performance:

```clojure
(app/fulcro-app
  {:render-middleware
   (fn [this real-render]
     ;; Profile rendering time
     (let [start (js/performance.now)]
       (try
         (real-render)
         (finally
           (let [duration (- (js/performance.now) start)]
             (when (> duration 16) ; Log renders slower than 60fps
               (log/warn "Slow render:" (comp/component-name this) duration "ms")))))))})
```

## Query Optimization

### Keep Queries Minimal
Only query the data your component actually needs:

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

### Focused Query Loading
Use the `:focus` option to load a subset of your component's query:

```clojure
;; Load only the essential initial data
(df/load! this :person Person 
  {:focus [:person/id :person/name :person/avatar]})

;; Load additional data on demand when needed
(defsc PersonDetail [this {:person/keys [id name email] :as props}]
  {:componentDidMount
   (fn [this]
     ;; Load additional details only when the component mounts
     (when (and id (not email))
       (df/load! this [:person/id id] PersonDetail
         {:focus [:person/email :person/phone :person/address]})))}
  ...)
```

Note: `:focus` restricts which fields from your component's query are loaded from the server.

## Data Loading Optimization

### Parallel vs Sequential Loading
Control how requests are batched and sent to the server:

```clojure
;; Parallel: Multiple requests sent immediately (not batched)
(df/load-field! this :background/long-query {:parallel true})

;; Sequential: Requests batched and sent together (default, more efficient)
(df/load-field! this :background/long-query {})

;; Load multiple resources, allowing them to be batched together
(df/load! this :recent-posts Post {})
(df/load! this :user-stats Stats {})
(df/load! this :notifications Notification {})
```

Sequential loading (the default) is generally preferred as it allows Fulcro to batch multiple requests into a single HTTP call for better performance.

### Incremental Loading
Load critical data first, then supplementary data:

```clojure
(defsc Dashboard [this props]
  {:componentDidMount
   (fn [this]
     ;; Load critical data first
     (df/load! this :user-profile UserProfile
       {:post-action (fn [env]
                       ;; After profile loads, load supplementary data
                       (df/load! this :user-preferences Preferences)
                       (df/load! this :recent-activity Activity))}))}
  ...)
```

### Caching Strategies
Avoid reloading unchanged data by tracking timestamps:

```clojure
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
Always clean up resources in `componentWillUnmount`:

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

### Event Listener Cleanup
Remove event listeners when components unmount:

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
For large lists, consider using virtualization libraries (e.g., react-virtual, react-window) that integrate with Fulcro:

```clojure
;; Example using external virtualization library
(defsc VirtualizedList [this {:keys [items]}]
  {:query [:items]}
  ;; Use react-virtual or similar library to render only visible items
  (dom/div
    (virtualize-list items render-item-fn)))
```

<!-- TODO: Verify the specific virtualization library integration patterns recommended for Fulcro -->

## Bundle Size Optimization

### Code Splitting
Use Shadow-cljs modules to split code into multiple bundles loaded on demand:

```clojure
;; shadow-cljs.edn
{:builds
 {:main
  {:target :browser
   :modules
   {:main     {:init-fn app.client/init}
    :admin    {:depends-on #{:main}
               :entries [app.admin]}
    :reports  {:depends-on #{:main}
               :entries [app.reports]}}}}}
```

Load modules dynamically in your router:

```clojure
(defsc AdminPage [this props]
  {:route-segment ["admin"]
   :will-enter    (fn [app params]
                    ;; Load admin module on demand
                    (-> (js/import "./admin.js")
                        (.then #(dr/route-immediate [:component/id ::AdminPage]))))}
  ...)
```

### Tree Shaking
Import only what you need to help your bundler eliminate unused code:

```clojure
;; Good: Import only what you need
(ns app.ui
  (:require [com.fulcrologic.fulcro.dom :as dom :refer [div span]]))

;; Avoid: Importing entire namespaces unnecessarily
(ns app.ui
  (:require [some.large.library :as lib])) ; Loads everything
```

### Dependency Optimization
Choose lighter alternatives when possible:

```clojure
;; Instead of moment.js (large), use date-fns (smaller, tree-shakeable)
(ns app.utils.date
  (:require ["date-fns/format" :as date-format]
            ["date-fns/parseISO" :as parse-iso]))

;; Prefer native JavaScript/Clojure for simple operations over heavy libraries
```

## Database Optimization

### Efficient Mutations
Update specific entities directly rather than reprocessing entire collections:

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
Group multiple updates into a single transaction:

```clojure
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
Fulcro's normalized database structure enables efficient updates:

```clojure
;; Good: Normalized reference (O(1) lookup and update)
{:current-user [:person/id 123]
 :person/id {123 {:person/id 123 :person/name "John"}}}

;; Avoid: Denormalized duplication (O(n) updates across duplicates)
{:current-user {:person/id 123 :person/name "John"}
 :recent-posts [{:author {:person/id 123 :person/name "John"}}]}
```

When data is normalized, updating a person's name in one location automatically reflects everywhere that person is referenced.

## Network Optimization

### Request Batching
Fulcro automatically batches requests to minimize HTTP calls:

```clojure
(app/fulcro-app
  {:remotes {:remote (http/fulcro-http-remote
                       {:batch-requests? true
                        :batch-timeout   100})}}) ; Batch requests within 100ms
```

Multiple load operations dispatched within the batch timeout are combined into a single HTTP request.

### Compression
Enable gzip compression for network traffic:

```clojure
;; Server-side (middleware)
(def middleware
  (-> handler
    (wrap-gzip)
    (wrap-api {...})))

;; Client-side request compression
(http/fulcro-http-remote
  {:request-middleware [(fn [req] (assoc req :compress-request? true))]})
```

### Caching Headers
Set appropriate cache headers on API responses:

```clojure
(defn api-handler [req]
  {:status 200
   :headers {"Cache-Control" "public, max-age=300"} ; 5 minute cache
   :body response-data})
```

## Measurement and Monitoring

### Performance Monitoring
Use the browser's performance APIs to measure rendering:

```clojure
(defn measure-render-time [app]
  (let [start (js/performance.now)]
    (app/schedule-render! app)
    (js/requestAnimationFrame
      #(log/info "Render time:" (- (js/performance.now) start) "ms"))))
```

### Memory Usage Tracking
Monitor heap usage in development:

```clojure
(defn log-memory-usage []
  (when (exists? js/performance.memory)
    (let [memory js/performance.memory]
      (log/info "Memory usage:"
                "Used:" (/ (.-usedJSHeapSize memory) 1024 1024) "MB"
                "Total:" (/ (.-totalJSHeapSize memory) 1024 1024) "MB"))))
```

### Query Performance Analysis
Add timing to load operations:

```clojure
(let [load-start (js/Date.now)]
  (df/load! this :people Person
    {:marker :people-loading
     :post-action (fn [env]
                    (let [duration (- (js/Date.now) load-start)]
                      (log/info "People loaded in" duration "ms")))}))
```

## Best Practices

### General Guidelines
- **Measure first**: Profile before optimizing to find actual bottlenecks
- **Optimize bottlenecks**: Focus on performance problems you've measured
- **Progressive optimization**: Start with the biggest wins
- **Monitor continuously**: Performance can regress over time

### Component Design
- **Use idents**: Components with `:ident` enable targeted rendering optimization
- **Pure rendering**: Avoid side effects in render functions
- **Stable keys**: Use consistent React keys for list items
- **Minimal props**: Pass only necessary data to components

### Data Patterns
- **Normalize data**: Use graph structure efficiently with proper idents
- **Batch operations**: Group related updates into single mutations
- **Lazy loading**: Load data when needed, not all upfront
- **Cache strategically**: Balance memory usage against network requests

### Development Workflow
- **Production builds**: Always test performance with optimized (release) builds
- **Realistic data**: Use production-like data volumes for testing
- **Device testing**: Test on target devices and network conditions
- **Continuous monitoring**: Track performance metrics over time
