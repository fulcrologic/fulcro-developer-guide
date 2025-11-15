
# Advanced Topics

## Overview
This chapter covers advanced Fulcro concepts including code splitting, logging, production debugging, React Native integration, and GraphQL interoperability.

## Code Splitting

Code splitting is fully covered in [Chapter 29: Code Splitting and Modules](./29-code-splitting.md). See that chapter for comprehensive coverage including:

- Module configuration in shadow-cljs
- DynamicRouter vs Union Router patterns
- Self-installation mechanisms with `cljs.loader/set-loaded!`
- Complete code splitting examples
- Best practices and common pitfalls

The key pattern for split modules is to register themselves at load time:

```clojure
;; shadow-cljs.edn
{:builds
 {:main
  {:target :browser
   :module-loader true
   :modules
   {:main   {:init-fn app.client/init
             :entries [app.client]}
    :admin  {:depends-on #{:main}
             :entries [app.admin]}
    :reports {:depends-on #{:main}
              :entries [app.reports]}}}}}

;; In split module (e.g., app/admin.cljs)
(ns app.admin
  (:require
    [com.fulcrologic.fulcro.components :refer [defsc]]
    [com.fulcrologic.fulcro.routing.legacy-ui-routers :as r]
    [com.fulcrologic.fulcro.dom :as dom]
    cljs.loader))

(defsc AdminPage [this props]
  {:initial-state (fn [_] {r/dynamic-route-key :admin})
   :ident (fn [] [:admin :singleton])
   :query [r/dynamic-route-key]}
  (dom/div "Admin Interface"))

;; Critical: Register with dynamic router
(defmethod r/get-dynamic-router-target :admin [k] AdminPage)

;; Critical: Mark module as loaded
(cljs.loader/set-loaded! :admin)
```

## Logging

Comprehensive logging coverage is available in [Chapter 30: Logging in Fulcro](./30-logging.md). Key points:

### Basic Log Levels

```clojure
(ns app.core
  (:require [taoensso.timbre :as log]))

;; Available log levels (lowest to highest)
(log/trace "Very detailed debug info")
(log/debug "Debug information")
(log/info "General information")
(log/warn "Warning messages")
(log/error "Error messages")
(log/fatal "Fatal errors")

;; Set global log level
(log/set-level! :debug)    ; Show debug and above
(log/set-level! :info)     ; Show info and above (default)
(log/set-level! :warn)     ; Show warnings and above only
```

### Structured Logging

```clojure
(defn log-user-action [user-id action details]
  (log/info "User action"
            {:user-id user-id
             :action action
             :details details
             :timestamp (js/Date.now)
             :session-id (get-session-id)}))

;; Usage
(log-user-action 123 :document-created {:doc-id 456 :doc-type :report})
```

### Performance Logging with Timing Macro

```clojure
(defmacro with-timing [operation-name & body]
  `(let [start# (js/performance.now)]
     (try
       ~@body
       (finally
         (let [duration# (- (js/performance.now) start#)]
           (log/debug "Operation timing"
                      {:operation ~operation-name
                       :duration-ms duration#}))))))

;; Usage
(with-timing "complex-calculation"
  (perform-complex-calculation data))
```

### Enhanced Console Output for Development

Use Fulcro's enhanced logging helpers for better error formatting:

```clojure
(ns app.development-preload
  (:require
    [taoensso.timbre :as log]
    [com.fulcrologic.fulcro.algorithms.timbre-support :refer [console-appender prefix-output-fn]]))

;; Enhanced logging setup for development
(log/set-level! :debug)
(log/merge-config!
  {:output-fn prefix-output-fn        ; Better formatting
   :appenders {:console (console-appender)}})
```

### Exception Logging

```clojure
;; CORRECT: Exception first for proper formatting
(try
  (risky-operation)
  (catch :default ex
    (log/error ex "Operation failed with context:" {:user-id 123})))

;; INCORRECT: Don't put exception last
(try
  (risky-operation)
  (catch :default ex
    (log/error "Operation failed:" ex)))
```

## Production Debugging

### Debug Information in Builds

```clojure
;; shadow-cljs.edn production debugging
{:builds
 {:main
  {:target :browser
   :compiler-options {:source-map true
                      :source-map-include-sources-content true}
   :closure-defines {goog.DEBUG false
                     app.config/DEBUG_ENABLED true}}}}
```

### Error Handling Patterns

Fulcro provides multiple ways to handle errors from remote operations:

```clojure
(ns app.errors
  (:require
    [com.fulcrologic.fulcro.mutations :refer [defmutation]]
    [com.fulcrologic.fulcro.data-fetch :as df]
    [taoensso.timbre :as log]))

;; Mutation with error handling
(defmutation error-mutation [params]
  (action [env]
    (js/alert "Optimistic action ran"))

  (error-action [{:keys [app ref result]}]
    (js/alert "Mutation error")
    (log/error "Mutation failed:" result))

  (remote [env] true))

;; Load with fallback on error
(defmutation handle-read-error [params]
  (action [env]
    (js/alert "There was a read error")
    (log/info "Load failed with:" (:result params))))

;; Usage
(df/load! this :data SomeComponent
  {:fallback `handle-read-error})
```

### Remote Error Detection

```clojure
(ns app.error-detection
  (:require
    [com.fulcrologic.fulcro.application :as app]
    [com.wsscode.pathom.core :as p]))

(defn contains-error?
  "Check if response contains Pathom error indicators."
  [body]
  (when (map? body)
    (let [values (vals body)]
      (reduce
        (fn [error? v]
          (if (or
                (and (map? v) (contains? (set (keys v)) ::p/reader-error))
                (= v ::p/reader-error))
            (reduced true)
            error?))
        false
        values))))

(def app
  (app/fulcro-app
    {:remote-error? (fn [{:keys [body] :as result}]
                      (or
                        (app/default-remote-error? result)
                        (contains-error? body)))}))
```

### Global Error Reporting

```clojure
(defn setup-error-reporting []
  (set! js/window.onerror
        (fn [message source lineno colno error]
          (log/error "Global error"
                     {:message message
                      :source source
                      :line lineno
                      :column colno
                      :error (str error)
                      :user-agent js/navigator.userAgent
                      :url js/location.href})
          ;; Send to error reporting service
          (report-error-to-service
            {:type "javascript-error"
             :message message
             :stack (when error (.-stack error))}))))

(defn report-fulcro-errors [app-instance]
  (app/set-global-error-handler!
    app-instance
    (fn [error-map]
      (log/error "Fulcro error" error-map)
      (report-error-to-service error-map))))
```

## React Native Integration

React Native integration is covered in detail in [Chapter 31: React Native Integration](./31-react-native.md). Key points:

- Use Expo for React Native development
- Wrap native components for Fulcro compatibility
- Share business logic between web and mobile
- Use `cljs.loader` for loading modules when available
- Test on actual devices for performance

The framework's data-driven architecture provides significant advantages for mobile development with excellent performance and code reuse opportunities.

## Workspaces Development

Component development workspaces are fully documented in [Chapter 28: Development Workspaces](./28-workspaces.md), which covers:

- Setting up workspaces with shadow-cljs
- Creating workspace cards for components
- Organizing workspace namespaces
- Testing components in isolation
- Design system development

Use workspaces to develop and test components before integrating them into your main application.

## Batched and Parallel Loading

Fulcro provides support for parallel data loads to improve performance when loading multiple pieces of data:

### Parallel Loading

```clojure
(ns app.demos.loading
  (:require
    [com.fulcrologic.fulcro.data-fetch :as df]
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    [com.fulcrologic.fulcro.dom :as dom]))

(defsc DataComponent [this {:keys [id data status]}]
  {:query (fn [] [:id :data [df/marker-table '_]])
   :ident [:data/by-id :id]}
  (let [loading-status (get-in this [df/marker-table [:fetching id]])]
    (dom/div
      (dom/button
        {:onClick #(df/load-field! this :data {:parallel true :marker [:fetching id]})}
        "Load in parallel")
      (if (df/loading? loading-status)
        (dom/span "Loading...")
        (dom/span data)))))
```

**Key points:**
- Use `:parallel true` to load multiple fields simultaneously
- Use `:marker` to track loading status for each field
- Sequential loading (default) waits for each load to complete

### Server-Side Resolver Batching

When the same resolver is called multiple times, Pathom can batch the requests if the resolver declares `::pc/batch? true`:

```clojure
(ns app.resolvers
  (:require [com.wsscode.pathom.connect :as pc]))

(pc/defresolver users-batch-resolver [env inputs]
  {::pc/input  #{:user/id}
   ::pc/output [:user/name :user/email]
   ::pc/batch? true}
  ;; env contains multiple inputs in batch
  ;; Return a map with results for each input
  (let [user-ids (map :user/id inputs)]
    ;; Single database query for all users
    (load-users-by-ids user-ids)))
```

<!-- TODO: Verify this claim -->
**Note**: Batching at the resolver level happens automatically through Pathom's query processing. The `::pc/batch? true` declaration tells Pathom to send multiple requests to this resolver together.

## GraphQL Integration

Fulcro can work with GraphQL APIs through custom remote implementations. This is an advanced pattern not commonly used since Fulcro's EQL provides similar capabilities. If you need GraphQL integration, consider:

1. Using GraphQL as a backend service behind a Pathom resolver layer
2. Implementing a custom HTTP remote that translates EQL to GraphQL queries
3. Leveraging Fulcro's data normalization to normalize GraphQL response data

For most use cases, the recommendation is to keep your server API in Fulcro's native format (using Pathom resolvers) for the best integration experience.

## Best Practices

### Performance Optimization
- **Lazy load modules**: Split code at route boundaries (see Chapter 29)
- **Use parallel loads**: Load independent data simultaneously with `:parallel true`
- **Profile before optimizing**: Measure actual bottlenecks with browser tools
- **Cache resolver results**: Use Pathom's caching mechanisms for expensive computations

### Error Handling
- **Graceful degradation**: Application should function with partial data failures
- **Comprehensive logging**: Capture enough context for debugging production issues
- **User-friendly errors**: Don't expose technical details to end users
- **Error recovery**: Provide ways to retry failed operations

### Development Workflow
- **Use workspaces**: Develop components in isolation (Chapter 28)
- **Enable source maps**: Debug production issues with source map support
- **Monitor performance**: Track real-world performance metrics
- **Test on actual devices**: Mobile performance differs significantly

### Architecture Guidelines
- **Keep abstractions thin**: Avoid over-abstracting platform differences
- **Share business logic**: Maintain platform-specific UI, shared data layer
- **Plan for offline**: Consider network reliability and offline behavior
- **Progressive enhancement**: Ensure basic functionality works everywhere
