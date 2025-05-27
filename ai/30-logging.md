# Logging in Fulcro

## Overview

Fulcro 3 uses [Timbre](https://github.com/ptaoussanis/timbre) for logging. Timbre provides comprehensive logging capabilities with configurable levels, appenders, and output formatting.

## Basic Timbre Usage

### Log Levels

```clojure
(ns my-app.core
  (:require [taoensso.timbre :as log]))

;; Available log levels (lowest to highest)
(log/trace "Very detailed debug info")
(log/debug "Debug information")
(log/info "General information")
(log/warn "Warning messages")
(log/error "Error messages")
(log/fatal "Fatal errors")
```

### Setting Log Levels

```clojure
;; Set global log level
(log/set-level! :debug)    ; Show debug and above
(log/set-level! :info)     ; Show info and above (default)
(log/set-level! :warn)     ; Show warnings and above only

;; Check current level
(log/get-level)            ; => :info

;; Conditional logging
(when (log/may-log? :debug)
  (log/debug "Expensive debug calculation:" (expensive-fn)))
```

### Basic Configuration

```clojure
(log/merge-config! 
  {:level :debug
   :appenders {:console {:enabled? true}}})
```

## Fulcro Logging Helpers

### The Problem with CLJS Error Messages

Stock Timbre in ClojureScript often produces poorly formatted error messages, especially when using tools like [Ghostwheel](https://github.com/gnl/ghostwheel) for spec instrumentation.

### Enhanced Console Output

```clojure
(ns app.development-preload
  (:require
    [taoensso.timbre :as log]
    [com.fulcrologic.fulcro.algorithms.timbre-support :refer [console-appender prefix-output-fn]]))

;; Enhanced logging setup for development
(log/set-level! :debug)
(log/merge-config! 
  {:output-fn prefix-output-fn        ; Better formatting
   :appenders {:console (console-appender)}}) ; Enhanced console output
```

### Prerequisites for Enhanced Logging

1. **Binaryage DevTools**: Add to dependencies (Shadow-cljs auto-includes when detected)
2. **Chrome Custom Formatters**: Enable in Chrome DevTools Console settings
3. **Development Preload**: Configure Shadow-cljs to preload logging setup

### Shadow-cljs Configuration

```clojure
;; shadow-cljs.edn
{:builds
 {:app
  {:target :browser
   :devtools {:preloads [app.development-preload]} ; Preload logging config
   :dev {:compiler-options {:closure-defines {goog.DEBUG true}}}}}}
```

## Logging Best Practices

### Exception Logging

**Important**: Log the exception object first for proper formatting:

```clojure
;; CORRECT: Exception first
(try
  (risky-operation)
  (catch :default ex
    (log/error ex "Operation failed with context:" {:user-id 123})))

;; INCORRECT: Exception not first
(try
  (risky-operation)
  (catch :default ex
    (log/error "Operation failed:" ex))) ; Poor formatting
```

### Structured Logging

```clojure
;; Good: Structured data
(log/info "User logged in" 
          {:user-id (:id user)
           :session-id session-id
           :timestamp (js/Date.)})

;; Less useful: String concatenation
(log/info (str "User " (:id user) " logged in"))
```

### Performance Considerations

```clojure
;; Use conditional logging for expensive operations
(when (log/may-log? :debug)
  (log/debug "Expensive data dump:" (expensive-data-transform)))

;; Or use delay for lazy evaluation
(log/debug "Data:" (delay (expensive-operation)))
```

## Production Logging Configuration

### Level-based Elision

```clojure
;; Production config - remove debug/trace statements entirely
(log/merge-config!
  {:level :info
   :middleware [(fn [data]
                  (when (>= (log/level-int (:level data))
                            (log/level-int :info))
                    data))]})
```

### Custom Appenders

```clojure
;; Send errors to external service
(defn error-service-appender []
  {:enabled? true
   :async? true
   :min-level :error
   :fn (fn [data]
         (when-let [error (:?err data)]
           (send-to-error-service
             {:message (:msg_ data)
              :error error
              :context (:context data)})))})

(log/merge-config!
  {:appenders {:error-service (error-service-appender)}})
```

### Browser vs Server Configuration

```clojure
;; Conditional configuration
#?(:cljs
   (log/merge-config!
     {:appenders {:console (console-appender)}})
   :clj
   (log/merge-config!
     {:appenders {:spit {:spit-filename "app.log"}}}))
```

## Fulcro-Specific Logging

### Transaction Logging

```clojure
(defn log-transaction-middleware [tx]
  (fn [result]
    (log/debug "Transaction completed:" 
               {:tx tx 
                :result (dissoc result :com.fulcrologic.fulcro.algorithms.tx-processing/id)})
    result))

;; Add to app configuration
(app/fulcro-app
  {:tx-processing-middleware [log-transaction-middleware]})
```

### Network Request Logging

```clojure
(defn logging-request-middleware [handler]
  (fn [request]
    (log/debug "Sending request:" (dissoc request :body))
    (handler request)))

(defn logging-response-middleware [handler]
  (fn [response]
    (log/debug "Received response:" 
               {:status (:status-code response)
                :body-keys (when (map? (:body response)) 
                            (keys (:body response)))})
    (handler response)))

;; Add to remote configuration
(http-remote/fulcro-http-remote
  {:request-middleware (-> http-remote/wrap-fulcro-request
                           logging-request-middleware)
   :response-middleware (-> http-remote/wrap-fulcro-response
                            logging-response-middleware)})
```

### Component Lifecycle Logging

```clojure
(defsc MyComponent [this props]
  {:componentDidMount
   (fn [this]
     (log/debug "Component mounted:" {:component (comp/component-name this)
                                     :props (comp/props this)}))
   :componentWillUnmount
   (fn [this]
     (log/debug "Component unmounting:" {:component (comp/component-name this)}))}
  ...)
```

## Advanced Logging Patterns

### Contextual Logging

```clojure
;; Add context to all logs in a scope
(log/with-context {:user-id current-user-id}
  (log/info "Processing request")
  (process-request)
  (log/info "Request completed"))
```

### Conditional Debug Features

```clojure
(def debug-enabled? 
  ^boolean goog.DEBUG) ; Closure define for debug builds

(defn debug-log [& args]
  (when debug-enabled?
    (apply log/debug args)))

;; Usage
(debug-log "Debug info:" data)
```

### Log Filtering

```clojure
;; Filter out noisy logs
(defn filter-middleware [data]
  (when-not (and (= :debug (:level data))
                 (re-find #"noisy-component" (str (:msg_ data))))
    data))

(log/merge-config!
  {:middleware [filter-middleware]})
```

### Performance Monitoring

```clojure
(defn timed-operation [operation-name f]
  (let [start (js/performance.now)]
    (try
      (let [result (f)]
        (log/info "Operation completed:"
                  {:operation operation-name
                   :duration-ms (- (js/performance.now) start)})
        result)
      (catch :default e
        (log/error e "Operation failed:"
                   {:operation operation-name
                    :duration-ms (- (js/performance.now) start)})
        (throw e)))))

;; Usage
(timed-operation "user-save"
  #(save-user user-data))
```

## Development vs Production

### Development Configuration

```clojure
;; development-preload.cljs
(ns app.development-preload
  (:require
    [taoensso.timbre :as log]
    [com.fulcrologic.fulcro.algorithms.timbre-support :refer [console-appender prefix-output-fn]]))

(log/set-level! :debug)
(log/merge-config!
  {:output-fn prefix-output-fn
   :appenders {:console (console-appender)}})

(log/info "Development logging initialized")
```

### Production Configuration

```clojure
;; production-config.cljs
(ns app.production-config
  (:require [taoensso.timbre :as log]))

(log/set-level! :warn)
(log/merge-config!
  {:appenders 
   {:console {:enabled? false}  ; Disable console logging
    :remote {:enabled? true
             :min-level :error
             :fn (fn [data]
                   (when-let [error (:?err data)]
                     (send-error-to-service data)))}}})
```

### Build-time Configuration

```clojure
;; Use closure defines for build-time configuration
(def log-level 
  #?(:cljs goog.LOG_LEVEL
     :clj "info"))

(defn configure-logging! []
  (log/set-level! (keyword log-level))
  #?(:cljs
     (when goog.DEBUG
       (log/merge-config! {:appenders {:console (console-appender)}}))))
```

## Common Patterns

### Error Boundaries with Logging

```clojure
(defn error-boundary-logger [error error-info component]
  (log/error error "React error boundary triggered:"
             {:component (comp/component-name component)
              :error-info error-info}))

(app/fulcro-app
  {:render-error error-boundary-logger})
```

### Mutation Logging

```clojure
(defmutation save-user [user-data]
  (action [{:keys [state]}]
    (log/info "Saving user:" {:user-id (:user/id user-data)})
    (swap! state update-user user-data))
  (remote [env] 
    (log/debug "Sending user save to server")
    true)
  (ok-action [env]
    (log/info "User saved successfully"))
  (error-action [{:keys [result]}]
    (log/error "User save failed:" result)))
```

Effective logging is crucial for debugging, monitoring, and maintaining Fulcro applications. The enhanced Timbre support in Fulcro provides excellent development experience while maintaining production performance.