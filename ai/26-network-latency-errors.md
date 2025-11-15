
# Network Latency, Error Handling, and User Experience

## Overview

Modern applications must handle network latency gracefully while providing excellent user experience. Fulcro's optimistic updates allow near-zero latency perception, but applications also need strategies for network failures, progress indication, and error recovery.

## Global Network Activity Tracking

### Monitoring Active Remotes

Fulcro tracks network activity in the state database under `:com.fulcrologic.fulcro.application/active-remotes`:

```clojure
{:com.fulcrologic.fulcro.application/active-remotes #{:remote :auth-api}}
```

The value is a **set** of remote names with pending operations. When there are no pending operations, the set is empty.

### Component Network Status

```clojure
(defsc GlobalStatus [this {::app/keys [active-remotes]}]
  {:query [[::app/active-remotes '_]]
   :ident (fn [] [:component/id :activity])
   :initial-state {}}
  (let [loading? (boolean (seq active-remotes))]
    (when loading?
      (dom/div
        {:className "loading-overlay"}
        (dom/div "Loading...")))))
```

**Important**: Components querying for network status will re-render frequently. Keep them as UI leaves to avoid triggering subtree re-renders.

### Global Activity Component

```clojure
(defsc NetworkIndicator [this {::app/keys [active-remotes]}]
  {:query [[::app/active-remotes '_]]
   :ident (fn [] [:component/id :network-indicator])
   :initial-state {}}
  (let [active-count (count active-remotes)
        specific-loading {:remote (contains? active-remotes :remote)
                         :auth (contains? active-remotes :auth-api)}]
    (dom/div
      {:className (when (pos? active-count) "network-active")}
      (when (:remote specific-loading)
        (dom/span "Syncing data..."))
      (when (:auth specific-loading)
        (dom/span "Authenticating...")))))
```

## Pessimistic Operation Strategies

### Default Optimistic vs Pessimistic

Fulcro is optimistic by default but supports several pessimistic patterns:

#### 1. Load API Blocking

```clojure
(defn save-and-reload [this form-data]
  (comp/transact! this
    [(save-form form-data)
     (df/load! this :current-form Form {:post-mutation `form-saved})])
  ;; UI remains responsive, but follow-up load happens after mutation completes
  )
```

#### 2. Mutation Action/Result Pattern

```clojure
(defmutation save-critical-data [data]
  (action [{:keys [state]}]
    ;; Block UI optimistically
    (swap! state assoc-in [:ui/global :saving?] true))
  (remote [env] true)
  (ok-action [{:keys [state result]}]
    ;; Unblock UI on success
    (swap! state assoc-in [:ui/global :saving?] false)
    (swap! state assoc-in [:ui/global :message] "Saved successfully!"))
  (error-action [{:keys [state result]}]
    ;; Unblock UI on error
    (swap! state assoc-in [:ui/global :saving?] false)
    (swap! state assoc-in [:ui/global :error] "Save failed. Please try again.")))
```

**Note**: `ok-action` and `error-action` receive an environment map with keys like `:state`, `:result`, `:app`, and `:ref`. These are only called if using the default `result-action` handler.

#### 3. Pessimistic Transactions

```clojure
;; Each mutation completes (full-stack) before the next begins
(comp/transact! this
  [(submit-form form-data)
   (process-result)
   (update-ui-state)]
  {:optimistic? false})

;; Simplified: single pessimistic mutation
(comp/transact! this [(submit-form form-data)] {:optimistic? false})
```

### Advanced Global Control

#### Custom Default Result Action

```clojure
(def app
  (app/fulcro-app
    {:default-result-action
     (fn [env]
       ;; Call default behavior first
       (app/default-result-action! env)
       ;; Add global logging
       (when (:error env)
         (js/console.error "Global error:" (:error env))))}))
```

**Note**: The `default-result-action` is called after every remote operation. It invokes either `ok-action` or `error-action` based on the remote result.

#### Custom Load Error Handling

```clojure
;; Most commonly: use :error-action option on load
(df/load! this :users User
          {:error-action `handle-load-error})

(defmutation handle-load-error [{:keys [error load-params]}]
  (action [{:keys [state]}]
    ;; error contains the error details
    ;; load-params contains the original load parameters
    (swap! state assoc-in [:ui/users :error]
           "Failed to load users. Please try again.")))
```

The `:default-load-error-action` in app config is called for load failures without explicit `:error-action`.

## Full-Stack Error Handling Philosophy

### Error Categories and Responses

#### 1. Bugs (Unexpected Errors)
- **Cause**: Programming errors, edge cases
- **Response**: Cannot be predicted or handled gracefully
- **Strategy**: Global error handler, logging, graceful degradation

#### 2. Security Violations
- **Cause**: Malicious requests, authorization failures
- **Response**: Server-side logging, client receives generic error
- **Strategy**: Treat as bugs - unexpected in properly functioning UI

#### 3. User Perspective Outages (Network Issues)
- **Cause**: WiFi issues, mobile network problems
- **Response**: Potentially recoverable with retries
- **Strategy**: Block UI, retry with exponential backoff

#### 4. Infrastructure Outages
- **Cause**: Server crashes, database failures
- **Response**: Unrecoverable during outage
- **Strategy**: Graceful degradation, offline operation

### Programming with Pure Optimism

The Fulcro philosophy: **Only trigger optimistic updates when you expect server success.**

```clojure
;; Good: UI prevents invalid operations
(defn submit-form [this form-data]
  (when (and (form/valid? form-data)
           (not (form/submitting? form-data)))
    (comp/transact! this [(save-form form-data)])))

;; Better: Server validation as security, not UX
(defmutation save-form [form-data]
  (action [{:keys [state]}]
    ;; Client already validated, this should succeed
    (swap! state form/mark-submitting form-data))
  (remote [env] true))
```

### Treating Responses as Data, Not Errors

```clojure
;; Instead of login "errors", treat as responses
(defmutation attempt-login [credentials]
  (remote [env] true)
  (ok-action [{:keys [state result]}]
    (let [login-result (:login-attempt result)]
      (if (:success? login-result)
        (swap! state assoc :current-user (:user login-result))
        (swap! state assoc-in [:ui/login :message] (:message login-result))))))
```

### Global Error Handler Strategy

```clojure
(defn global-error-handler [env]
  (let [{:keys [error result]} env]
    ;; Log for debugging
    (js/console.error "Global error occurred:" error result)

    ;; Show user-friendly modal
    (swap! (::app/state-atom env)
           assoc :ui/error-modal
           {:open? true
            :message "An unexpected error occurred. Please try again."
            :can-retry? true})

    ;; Optional: Submit error report
    (submit-error-report env)))

(def app
  (app/fulcro-app
    {:global-error-action global-error-handler}))
```

## Flaky Network Handling

### Custom Networking with Retries

```clojure
(defn retry-remote [base-remote]
  (assoc base-remote
    :transmit!
    (fn [remote send-node]
      (let [max-retries 3
            retry-count (atom 0)
            ;; Get handler from send-node using correct key
            base-result-handler (:com.fulcrologic.fulcro.algorithms.tx-processing/result-handler send-node)]
        (letfn [(attempt-send []
                  (let [retry-handler
                        (fn [result]
                          (if (and (network-error? result)
                                   (< @retry-count max-retries))
                            (do
                              (swap! retry-count inc)
                              ;; Exponential backoff
                              (js/setTimeout attempt-send
                                           (* 1000 (Math/pow 2 @retry-count))))
                            ;; Success or max retries reached
                            (base-result-handler result)))]
                    ;; Replace result handler with retry handler
                    ((:transmit! base-remote)
                     remote
                     (assoc send-node
                       :com.fulcrologic.fulcro.algorithms.tx-processing/result-handler
                       retry-handler))))]
          (attempt-send))))))

(def app
  (app/fulcro-app
    {:remotes {:remote (retry-remote (http-remote/fulcro-http-remote {}))}}))
```

### Idempotent Server Operations

```clojure
;; Server-side mutation with idempotency
(defmutation server-create-user [user-data]
  (action [env]
    (let [existing-user (find-user-by-email (:email user-data))]
      (if existing-user
        ;; Idempotent: return existing user
        {:user existing-user}
        ;; Create new user
        {:user (create-user user-data)}))))
```

### Offline Operation Support

```clojure
(defn offline-capable-remote [base-remote]
  (let [pending-queue (atom [])]
    (assoc base-remote
      :transmit!
      (fn [remote send-node]
        (if (online?)
          ;; Process normally
          ((:transmit! base-remote) remote send-node)
          ;; Queue for later
          (do
            (swap! pending-queue conj send-node)
            ;; Return queued status to app using correct key
            (let [result-handler (:com.fulcrologic.fulcro.algorithms.tx-processing/result-handler send-node)]
              (result-handler
               {:status :queued
                :body {}
                :status-code 200}))))))))
```

## UI Blocking Patterns

### Blocking with Result Actions

```clojure
(defmutation save-important-data [data]
  (action [{:keys [state]}]
    ;; Block UI immediately
    (swap! state assoc-in [:ui/global :blocked?] true)
    (swap! state assoc-in [:ui/global :status] "Saving..."))
  (remote [env] true)
  (ok-action [{:keys [state result]}]
    ;; Unblock on success
    (swap! state assoc-in [:ui/global :blocked?] false)
    (swap! state assoc-in [:ui/global :status] "Saved successfully"))
  (error-action [{:keys [state result]}]
    ;; Unblock on error
    (swap! state assoc-in [:ui/global :blocked?] false)
    (swap! state assoc-in [:ui/global :status] "Save failed")))

;; Component using blocking state
(defsc SaveForm [this {:ui/keys [blocked? status]} form-data]
  {:query [:ui/blocked? :ui/status :form/data]}
  (dom/div
    {:className (when blocked? "blocked")}
    (when blocked?
      (dom/div {:className "overlay"} status))
    (form/render-form form-data)))
```

### Timeout-Based Blocking

```clojure
(defmutation save-with-timeout [data]
  (action [{:keys [state]}]
    (swap! state assoc-in [:ui/save :status] "saving")
    ;; Set timeout fallback
    (js/setTimeout
      #(swap! state assoc-in [:ui/save :status] "timeout")
      10000))
  (remote [env] true)
  (ok-action [{:keys [state result]}]
    (swap! state assoc-in [:ui/save :status] "saved"))
  (error-action [{:keys [state result]}]
    (swap! state assoc-in [:ui/save :status] "error")))
```

## Custom Error Detection

### Defining Remote Errors

```clojure
;; Pathom includes error markers in response
(defn contains-error? [body]
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
                      (or (app/default-remote-error? result)
                          (contains-error? body)))}))
```

**Note**: The default `remote-error?` checks HTTP status codes. Custom implementations should also check for HTTP errors or timeout conditions.

### Error Response Transformation

```clojure
;; Middleware approach: convert errors to data
(defn error-response-middleware [handler]
  (fn [response]
    (let [{:keys [status-code body]} response]
      (if (not= 200 status-code)
        ;; Transform error to data (so it's not treated as error-action)
        (handler
          (assoc response
                 :body {:app/error {:type :network
                                   :code status-code
                                   :message "Network request failed"}}
                 :original-transaction [:app/error]
                 :status-code 200))
        (handler response)))))

;; Or use custom remote-error? to decide what is an error
```

## Load Error Handling

### Load-Specific Error Mutations

```clojure
(df/load! this :users User
          {:error-action `handle-user-load-error
           :target [:ui/users]})

(defmutation handle-user-load-error [{:keys [error load-params]}]
  (action [{:keys [state]}]
    ;; error contains the error details
    ;; load-params contains the original load parameters
    (swap! state assoc-in [:ui/users :error]
           "Failed to load users. Please try again.")))
```

### Global Load Error Handler

```clojure
(defmutation global-load-error [{:keys [error load-params]}]
  (action [{:keys [state]}]
    (swap! state update :ui/global-errors conj
           {:type :load-error
            :query (:original-transaction load-params)
            :message "Failed to load data"
            :error error})))

(def app
  (app/fulcro-app
    {:default-load-error-action `global-load-error}))
```

## Request Abortion

### Basic Abort Usage

```clojure
;; Start request with abort ID
(df/load! this :users User {:abort-id :user-load})
(comp/transact! this [(save-data data)] {:abort-id :save-operation})

;; Abort requests
(app/abort! this :user-load)
(app/abort! this :save-operation)
```

### Abort with Navigation

```clojure
(defn navigate-away [this new-route]
  ;; Abort any pending operations for current route
  (app/abort! this :current-route-data)
  ;; Navigate to new route
  (dr/change-route this new-route))
```

### Abort Result Handling

```clojure
(defmutation save-data [data]
  (remote [env] true)
  (error-action [{:keys [result]}]
    ;; Check if this was an abort vs. actual error
    (if (::app/aborted? result)
      ;; Handle abortion specifically
      (js/console.log "Save operation was cancelled")
      ;; Handle other errors
      (show-error-message "Save failed"))))
```

<!-- TODO: Verify the exact key for abort status in result map -->

## Progress Updates

### Mutation Progress Tracking

Progress-action is called during long-running operations (typically file uploads):

```clojure
(defmutation upload-file [file-data]
  (action [{:keys [state]}]
    (swap! state assoc-in [:ui/upload :progress] 0))
  (progress-action [{:keys [state] :as env}]
    ;; Called periodically with progress information
    ;; NOTE: progress-action is only supported by HTTP remote
    (let [progress (http-remote/overall-progress env)]
      (swap! state assoc-in [:ui/upload :progress] progress)))
  (remote [env] true))

;; Component showing progress
(defsc FileUpload [this {:ui/keys [progress]}]
  {:query [:ui/progress]}
  (dom/div
    (dom/div {:className "progress-bar"}
      (dom/div {:className "progress-fill"
                :style {:width (str progress "%")}}))
    (dom/span (str progress "% complete"))))
```

**Important**: The HTTP remote supports `progress-action`. Custom remotes must explicitly call the update handler to provide progress updates.

### Progress Helpers

```clojure
(defmutation large-data-transfer [data]
  (progress-action [{:keys [state] :as env}]
    (swap! state assoc-in [:transfer :stats]
           {:overall (http-remote/overall-progress env)
            :send (http-remote/send-progress env)
            :receive (http-remote/receive-progress env)}))
  (remote [env] true))
```

These helpers are specific to the HTTP remote and won't work with custom remotes.

### Custom Remote Progress Support

```clojure
;; Custom remotes can simulate progress by calling update-handler
(defn custom-remote-with-progress [base-remote]
  (assoc base-remote
    :transmit!
    (fn [remote send-node]
      (let [update-handler (:com.fulcrologic.fulcro.algorithms.tx-processing/update-handler send-node)]
        ;; Simulate progress updates
        (doseq [progress [25 50 75 100]]
          (js/setTimeout #(update-handler {:progress progress}) (* 100 progress)))
        ((:transmit! base-remote) remote send-node)))))
```

This comprehensive approach to network latency and error handling ensures robust applications that gracefully handle the complexities of distributed systems while maintaining excellent user experience.
