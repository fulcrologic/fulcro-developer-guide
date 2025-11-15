
# Client Networking with Fulcro HTTP Remote

## Overview

Fulcro supports any number of remote handlers for communicating with multiple servers. The default HTTP remote uses JSON-encoded Transit over HTTP POST to `/api`, but is highly customizable through middleware.

## Default HTTP Remote

### Basic Features

- **Multiple Remotes**: Define any number of remote endpoints
- **Middleware Pipeline**: Customizable request/response processing
- **Abort Support**: Works with `app/abort!` 
- **Progress Reporting**: Updates UI during operations via `progress-action`
- **Transit Communication**: EDN data structures over the wire

### Default Configuration

```clojure
;; Default remote (automatic)
:remote (http-remote/fulcro-http-remote {:url "/api"})
```

### Custom Remote Creation

```clojure
(ns my-app.networking
  (:require [com.fulcrologic.fulcro.networking.http-remote :as http-remote]))

;; Basic custom remote
(def my-remote
  (http-remote/fulcro-http-remote 
    {:url "/api"
     :request-middleware custom-request-middleware
     :response-middleware custom-response-middleware}))

;; Multiple remotes
(def app
  (app/fulcro-app
    {:remotes {:remote my-remote
               :auth-api (http-remote/fulcro-http-remote {:url "/auth"})
               :analytics (http-remote/fulcro-http-remote {:url "/analytics"})}}))
```

## Request Middleware

### Default Request Processing

The default middleware `wrap-fulcro-request`:
- Converts body to JSON-encoded Transit
- Adds appropriate content-type headers
- Handles standard Fulcro request format

### Request Structure

Fulcro provides middleware with:

```clojure
{:body    [...] ; EDN transaction (query or mutations)
 :headers {}    ; Empty map (add your headers)  
 :url     "/api" ; Default URL for this remote
 :method  :post} ; HTTP verb (always :post by default)
```

### Custom Request Middleware

```clojure
(defn add-auth-header [handler]
  (fn [request]
    (handler
      (update request :headers assoc "Authorization" (get-auth-token)))))

(defn log-requests [handler]
  (fn [request]
    (js/console.log "Sending request:" (:body request))
    (handler request)))

;; Compose middleware (right to left evaluation)
;; IMPORTANT: Must include wrap-fulcro-request in the chain
(def request-middleware
  (-> http-remote/wrap-fulcro-request
      add-auth-header
      log-requests))
```

### Advanced Request Modification

```clojure
(defn add-csrf-token [handler]
  (fn [request]
    (handler
      (-> request
          (update :headers assoc "X-CSRF-Token" (get-csrf-token))
          (update :headers assoc "X-Requested-With" "XMLHttpRequest")))))

(defn custom-url-logic [handler]
  (fn [request]
    ;; Modify URL based on request characteristics
    (handler (assoc request :url (determine-url request)))))
```

### Final Request Format

Middleware output is used as:

- `:body` → Raw data for XhrIO send
- `:headers` → Map converted to JS object for request headers
- `:url` → Network target (relative or absolute)
- `:method` → Converted to uppercase HTTP verb string (e.g., "POST")

## Response Middleware

### Default Response Processing

The default middleware `wrap-fulcro-response` handles Transit decoding and standard API response format.

### Response Structure

Raw responses from the HTTP remote include:

```clojure
{:body                  ; Server response data
 :original-transaction  ; Original query/mutation sent (in Fulcro 3.1+)
 :status-code          ; HTTP status code  
 :status-text          ; HTTP status text
 :error                ; Error code (:network-error, :http-error, :timeout)
 :error-text           ; Error description string
 :outgoing-request}    ; Original request that triggered this response
```

<!-- TODO: Verify the exact keys in earlier Fulcro 3.x versions before 3.1 -->

### Custom Response Middleware

```clojure
(defn log-responses [handler]
  (fn [response]
    (js/console.log "Received response:" (:body response))
    (handler response)))

(defn handle-auth-errors [handler]
  (fn [response]
    (if (= 401 (:status-code response))
      (do
        (redirect-to-login!)
        response) ; Return response as-is to continue error processing
      (handler response))))
```

### Error Handling in Middleware

```clojure
(defn custom-error-handling [handler]
  (fn [response]
    (let [{:keys [error status-code]} response]
      (handler
        (if (not= 200 status-code)
          ;; Rewrite error as successful data merge
          ;; WARNING: Only use this if you understand how normalization works
          ;; The :transaction value should be a proper query for correct normalization
          (-> response
              (assoc :body {:app/error {:code status-code :message error}}
                     :original-transaction [:app/error]
                     :status-code 200)
              (dissoc :error))
          response)))))
```

### Response Rewriting

Middleware can modify:
- `:original-transaction` key → Changes how data is merged into state
- `:body` key → Modifies data being merged
- Error fields → Can convert errors to successful merges

**Warning**: Rewriting transactions affects normalization and merge behavior. Use component queries for proper normalization, and be careful with the `:transaction` key - it should match an actual query shape to ensure data is normalized correctly.

## Advanced Response Processing

### State Merging Pipeline

1. **Mutation Processing**: Mutation keys extracted and processed for tempid migrations
2. **Data Merging**: Remaining key/value pairs merged using provided transaction as query

### Example: Error to Data Conversion

```clojure
(defn errors-as-data [handler]
  (fn [response]
    (let [{:keys [error status-code]} response]
      (handler
        (cond
          ;; Network timeout
          (= :timeout error)
          (assoc response 
                 :body {:app/network-status {:status :timeout}}
                 :original-transaction [:app/network-status]
                 :status-code 200)
          
          ;; Server error  
          (>= status-code 500)
          (assoc response
                 :body {:app/network-status {:status :server-error}}
                 :original-transaction [:app/network-status] 
                 :status-code 200)
          
          ;; Client error
          (>= status-code 400)
          (assoc response
                 :body {:app/network-status {:status :client-error}}
                 :original-transaction [:app/network-status]
                 :status-code 200)
          
          :else response)))))
```

## Writing Custom Remote Implementations

### Remote Requirements

A remote is a map that **must** contain:
- `:transmit!` key with function `(fn [remote send-node] ...)`

The remote implementation must call the `result-handler` from the send-node exactly once per request, with a result map.

### Send Node Specification

```clojure
{:com.fulcrologic.fulcro.algorithms.tx-processing/id            ; Unique ID for this request
 :com.fulcrologic.fulcro.algorithms.tx-processing/idx           ; Index in the batch
 :com.fulcrologic.fulcro.algorithms.tx-processing/ast           ; Request AST (the query/mutation structure)
 :com.fulcrologic.fulcro.algorithms.tx-processing/result-handler ; (fn [result]) - call once with result
 :com.fulcrologic.fulcro.algorithms.tx-processing/update-handler ; (fn [progress-report]) - call for progress updates
 :com.fulcrologic.fulcro.algorithms.tx-processing/active?       ; Atom indicating if this request is active
 :com.fulcrologic.fulcro.algorithms.tx-processing/options}      ; Options map (e.g., contains :abort-id)
```

### Example: Local Storage Remote

```clojure
(defn local-storage-remote []
  {:transmit! (fn [remote send-node]
                (let [ast (:com.fulcrologic.fulcro.algorithms.tx-processing/ast send-node)
                      result-handler (:com.fulcrologic.fulcro.algorithms.tx-processing/result-handler send-node)]
                  ;; Process request against localStorage
                  (let [result (process-local-storage-request ast)]
                    ;; Must call result-handler exactly once
                    (result-handler 
                      {:body result
                       :original-transaction (:children ast)
                       :status-code 200}))))})
```

### Example: WebSocket Remote

```clojure
(defn websocket-remote [ws-url]
  (let [socket (js/WebSocket. ws-url)]
    {:transmit! (fn [remote send-node]
                  (let [ast (:com.fulcrologic.fulcro.algorithms.tx-processing/ast send-node)
                        result-handler (:com.fulcrologic.fulcro.algorithms.tx-processing/result-handler send-node)
                        request-id (random-uuid)]
                    ;; Send request
                    (.send socket (transit/write-str {:id request-id :ast ast}))
                    ;; Set up response handler
                    (set! (.-onmessage socket)
                          (fn [event]
                            (let [response (transit/read-str (.-data event))]
                              (when (= request-id (:id response))
                                (result-handler
                                  {:body (:data response)
                                   :original-transaction (:children ast) 
                                   :status-code 200})))))))}))
```

## Interfacing with External APIs

### Standard Result Format for Custom Remotes

When writing a custom remote, the result passed to `result-handler` should follow this standard format:

```clojure
{:body some-edn                    ; The response data to merge
 :original-transaction original-tx ; The query/mutation that prompted this response
 :status-code n}                   ; HTTP status code (200 for success)
```

Following this format ensures compatibility with Fulcro's default merge, error handling, and ok/result-action processing. If you deviate from this format, you'll need to handle merging and error processing yourself.

<!-- TODO: Note about Pathom integration - the REST and GraphQL examples in older Fulcro docs are for Fulcro 2.x. For Fulcro 3.x, consult Pathom's current documentation for the proper integration approach. -->

## Error Handling Strategies

### Global Error Detection

```clojure
(def app
  (app/fulcro-app
    {:remote-error? (fn [result]
                      (or (not= 200 (:status-code result))
                          (contains? (:body result) :error)
                          (contains? (:body result) :errors)))}))
```

### Mutation-Level Error Handling

```clojure
(defmutation save-user [user-data]
  (action [{:keys [state]}]
    ;; Optimistic update
    (swap! state assoc-in [:user/id (:user/id user-data)] user-data))
  (remote [env] true)
  (error-action [{:keys [state]} error]
    ;; Revert optimistic update on error
    (swap! state update :user/id dissoc (:user/id user-data))
    ;; Show error message
    (swap! state assoc :app/error-message "Failed to save user")))
```

### Custom Error Components

```clojure
(defn handle-network-errors [handler]
  (fn [response]
    (if (:error response)
      (do
        ;; Log error for debugging
        (js/console.error "Network error:" response)
        ;; Convert to app data
        (handler
          (assoc response
                 :body {:app/notifications [{:type :error 
                                             :message "Network operation failed"}]}
                 :original-transaction [{:app/notifications (comp/get-query Notification)}]
                 :status-code 200)))
      (handler response))))
```

## Best Practices

1. **Compose Middleware Carefully**: Always include default Fulcro middleware in your stack (e.g., `wrap-fulcro-request`, `wrap-fulcro-response`)
2. **Handle Errors Gracefully**: Convert errors to data when appropriate, but understand the implications for merge and normalization
3. **Log for Debugging**: Add logging middleware for development
4. **Secure Headers**: Add authentication and CSRF tokens via request middleware
5. **Progress Indication**: Use update handlers for long operations
6. **Timeout Handling**: Implement reasonable timeout strategies
7. **Retry Logic**: Add retry middleware for transient failures
8. **Result Handler**: Custom remotes must call `result-handler` exactly once per request

## Complete Example

```clojure
(ns my-app.networking
  (:require [com.fulcrologic.fulcro.networking.http-remote :as http-remote]))

(defn add-auth [handler]
  (fn [request]
    (handler
      (update request :headers assoc 
              "Authorization" (str "Bearer " (get-auth-token))))))

(defn handle-errors [handler]
  (fn [response]
    (let [{:keys [status-code error]} response]
      (cond
        (= 401 status-code) 
        (do (logout!) response)
        
        (= 403 status-code)
        (handler (assoc response
                        :body {:app/error "Access denied"}
                        :original-transaction [:app/error]
                        :status-code 200))
        
        :else (handler response)))))

(def api-remote
  (http-remote/fulcro-http-remote
    {:url "/api"
     :request-middleware (-> http-remote/wrap-fulcro-request add-auth)
     :response-middleware (-> http-remote/wrap-fulcro-response handle-errors)}))

(def app
  (app/fulcro-app
    {:remotes {:remote api-remote}}))
```

This networking system provides the flexibility to handle any communication pattern while maintaining Fulcro's data-driven architecture.
