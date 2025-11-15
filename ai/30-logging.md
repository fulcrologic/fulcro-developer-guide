
# Logging in Fulcro

## Overview

Fulcro 3 uses [Timbre](https://github.com/ptaoussanis/timbre) for logging. Read the Timbre documentation for detailed information about changing logging levels, eliding logging statements, and configuration options.

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
```

## Fulcro Logging Helpers

### The Problem with CLJS Error Messages

The logging output of errors in ClojureScript leaves something to be desired when used with stock Timbre. The js console error messages can be poorly formatted, especially when using tools like [Ghostwheel](https://github.com/gnl/ghostwheel) for spec instrumentation, or [expound](https://github.com/bhb/expound) for better spec error messages.

### Enhanced Console Output

Fulcro provides helpers in the `com.fulcrologic.fulcro.algorithms.timbre-support` namespace to improve error message formatting.

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
2. **Chrome Custom Formatters**: Enable in Chrome DevTools Console settings - this will print ClojureScript data as ClojureScript instead of raw JavaScript
3. **Development Preload**: Configure Shadow-cljs to preload your logging setup

### Shadow-cljs Configuration

```clojure
;; shadow-cljs.edn
{:builds
 {:app
  {:target :browser
   :devtools {:preloads [app.development-preload]}}}}
```

## Logging Best Practices

### Exception Logging

**Important**: Log the exception object first for proper formatting. This is documented in Timbre but easy to miss:

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

## Production Logging Configuration

### Compile-Time Elision

Fulcro uses Timbre for logging. Several namespaces include high levels of debug or trace logging to help you debug development-time issues. Timbre uses macros for logging such that all performance overhead of logging under a certain level can be completely removed from the code on a release build.

See [compile time elision](https://github.com/ptaoussanis/timbre#log-levels-and-ns-filters-compile-time-elision) in the Timbre docs.

**WARNING**: Elision can be done via an ENVIRONMENT variable that must be set *before* the compiler runs. Environment variables cannot be changed by the running VM. Thus if you are running `shadow-cljs server`, you cannot set the env variable in another terminal and run a release build and expect it to work! If the server is running, then the command `shadow-cljs release X` just sends a command to the already-running server, which will *not* have the environment variable.

### Development vs Production

```clojure
;; Development - in your preload file
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

```clojure
;; Production - set higher log level, disable console
(log/set-level! :warn)
(log/merge-config!
  {:appenders {:console {:enabled? false}}})
```

## Fulcro-Specific Logging Examples

### Mutation Error Handling

```clojure
(defmutation save-user [user-data]
  (action [{:keys [state]}]
    (log/info "Saving user:" {:user-id (:user/id user-data)})
    (swap! state update-user user-data))
  (remote [env] true)
  (error-action [{:keys [result]}]
    (log/error "User save failed:" result)))
```

### Load Error Handling

```clojure
;; Mutation used as a fallback for load error
(defmutation read-error [params]
  (action [env]
    (log/info "Result from server:" (:result params))
    (log/info "Original load params:" (:load-params params))))

;; In component
(df/load! this :some-data SomeComponent 
  {:fallback `read-error})
```

### Component Lifecycle Logging

```clojure
(defsc MyComponent [this props]
  {:componentDidMount
   (fn [this]
     (log/debug "Component mounted"))
   :componentWillUnmount
   (fn [this]
     (log/debug "Component unmounting"))}
  ...)
```

## Common Configuration Patterns

### Application-Level Error Handling

You can define custom error detection for your application:

```clojure
(def app 
  (app/fulcro-app 
    {:remote-error? (fn [{:keys [body] :as result}]
                      (or
                        (app/default-remote-error? result)
                        (contains-custom-error? body)))}))
```

When a remote result is considered an error, the `error-action` section of mutations will be called, allowing you to log or handle the error appropriately.

## Summary

Effective logging is crucial for debugging and maintaining Fulcro applications. The key points are:

- Use Timbre's standard logging functions (`log/debug`, `log/info`, `log/error`, etc.)
- Configure enhanced console output using Fulcro's `console-appender` and `prefix-output-fn` helpers in development
- Enable Chrome custom formatters for better ClojureScript data visualization
- Log exceptions first when using `log/error` 
- Use compile-time elision to remove debug logging from production builds
- Structure log messages with data maps for better searchability
