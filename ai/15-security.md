# Security

## Overview
Security in Fulcro applications involves both client-side and server-side considerations, from query authorization to CSRF protection and secure data handling.

## Client-Side Security

### Query Security
Client queries should be validated and authorized on the server:

```clojure
;; Client can request anything, but server controls access
(defsc UserProfile [this {:user/keys [id name email salary]}]
  {:query [:user/id :user/name :user/email :user/salary]} ; Client includes salary
  (dom/div name " - " email))

;; Server-side authorization
(pc/defresolver user-resolver [env {:user/keys [id]}]
  {::pc/input  #{:user/id}
   ::pc/output [:user/name :user/email :user/salary]}
  (let [current-user (get-current-user env)]
    (if (or (= (:user/id current-user) id)
            (admin? current-user))
      (get-user-data id)
      ;; Only return public fields for other users
      (select-keys (get-user-data id) [:user/name :user/email]))))
```

### Sensitive Data Handling
Never store sensitive data in client state:

```clojure
;; BAD: Storing sensitive data in client
{:user/id 123
 :user/name "John"
 :user/password "secret123"     ; Never store passwords
 :user/ssn "123-45-6789"       ; Never store SSNs
 :user/credit-card "4111..."}   ; Never store payment info

;; GOOD: Only store necessary client data
{:user/id 123
 :user/name "John"
 :user/email "john@example.com"
 :user/roles #{:user :premium}}
```

### Input Validation
Always validate and sanitize user inputs:

```clojure
(defn validate-user-input [input]
  (-> input
      (update :user/name str/trim)
      (update :user/email str/lower-case)
      ;; Remove any HTML/script tags
      (update :user/bio #(str/replace % #"<[^>]*>" ""))))

(defmutation update-profile [params]
  (action [{:keys [state]}]
    (let [sanitized-params (validate-user-input params)]
      ;; Use sanitized data
      )))
```

## Server-Side Security

### Authentication
Implement proper authentication checks:

```clojure
(defn authenticated? [env]
  (boolean (get-in env [:request :session :user/id])))

(defn get-current-user [env]
  (when-let [user-id (get-in env [:request :session :user/id])]
    (get-user-by-id user-id)))

;; Use in resolvers
(pc/defresolver protected-data [env input]
  {::pc/output [:sensitive/data]}
  (if (authenticated? env)
    {:sensitive/data "secret information"}
    (throw (ex-info "Unauthorized" {:type :unauthorized}))))
```

### Authorization
Check permissions for specific operations:

```clojure
(defn authorize! [env required-permission resource-id]
  (let [current-user (get-current-user env)]
    (when-not (has-permission? current-user required-permission resource-id)
      (throw (ex-info "Forbidden" {:type :forbidden})))))

(pc/defmutation delete-document [env {:doc/keys [id]}]
  {::pc/sym `delete-document}
  (authorize! env :document/delete id)
  (delete-document-from-db id))
```

### Input Sanitization
Server-side input validation and sanitization:

```clojure
(defn sanitize-html [html-string]
  ;; Use a proper HTML sanitization library
  (html-sanitizer/sanitize html-string))

(defn validate-email [email]
  (and (string? email)
       (re-matches #".+@.+\..+" email)
       (< (count email) 255)))

(pc/defmutation update-user-bio [env {:user/keys [id bio]}]
  {::pc/sym `update-user-bio}
  (when-not (validate-html-content bio)
    (throw (ex-info "Invalid content" {:type :validation-error})))
  (let [sanitized-bio (sanitize-html bio)]
    (update-user-in-db id {:bio sanitized-bio})))
```

## CSRF Protection

### Server-Side CSRF Middleware
```clojure
(ns app.middleware.csrf
  (:require [ring.middleware.anti-forgery :refer [wrap-anti-forgery]]))

(def app-middleware
  (-> handler
      (wrap-anti-forgery {:error-response {:status 403 :body "CSRF token mismatch"}})
      ;; other middleware
      ))
```

### Client-Side CSRF Token Handling
```clojure
;; Include CSRF token in requests
(defn add-csrf-token [request]
  (let [csrf-token (-> js/document
                       (.querySelector "meta[name='csrf-token']")
                       (.-content))]
    (assoc-in request [:headers "x-csrf-token"] csrf-token)))

(def secure-remote
  (http/fulcro-http-remote
    {:request-middleware [add-csrf-token]}))
```

## Session Management

### Secure Session Configuration
```clojure
(def session-config
  {:store (memory-store)
   :cookie-attrs {:http-only true
                  :secure true        ; HTTPS only
                  :same-site :strict  ; CSRF protection
                  :max-age 3600}})    ; 1 hour expiry

(def app
  (-> handler
      (wrap-session session-config)))
```

### Session Validation
```clojure
(defn validate-session [env]
  (let [session (get-in env [:request :session])
        user-id (:user/id session)
        last-seen (:last-seen session)
        now (System/currentTimeMillis)]
    (and user-id
         last-seen
         (< (- now last-seen) (* 30 60 1000))))) ; 30 minutes

(defn refresh-session [env]
  (assoc-in env [:response :session :last-seen] (System/currentTimeMillis)))
```

## Data Access Patterns

### Principle of Least Privilege
Only return data the user needs and is authorized to see:

```clojure
(pc/defresolver user-list [env input]
  {::pc/output [{:users [:user/id :user/name :user/email]}]}
  (let [current-user (get-current-user env)
        base-fields [:user/id :user/name]]
    {:users (map (fn [user]
                   (if (admin? current-user)
                     (select-keys user [:user/id :user/name :user/email])
                     (select-keys user base-fields)))
                 (get-all-users))}))
```

### Field-Level Security
```clojure
(pc/defresolver user-resolver [env {:user/keys [id]}]
  {::pc/input  #{:user/id}
   ::pc/output [:user/name :user/email :user/phone :user/address]}
  (let [current-user (get-current-user env)
        user-data (get-user-by-id id)
        public-fields [:user/name]
        private-fields [:user/email :user/phone :user/address]]
    (cond
      ;; User can see their own data
      (= (:user/id current-user) id)
      user-data
      
      ;; Admins can see all fields
      (admin? current-user)
      user-data
      
      ;; Others only see public fields
      :else
      (select-keys user-data public-fields))))
```

## Error Handling Security

### Secure Error Responses
Don't leak sensitive information in error messages:

```clojure
(defn secure-error-handler [error env]
  (let [error-type (:type (ex-data error))
        public-message (case error-type
                         :unauthorized "Access denied"
                         :forbidden "Insufficient permissions"
                         :validation-error "Invalid input"
                         :not-found "Resource not found"
                         "An error occurred")]
    ;; Log detailed error for debugging
    (log/error error "Request error" {:env env})
    
    ;; Return generic message to client
    {:error public-message}))
```

### Rate Limiting
Prevent abuse with rate limiting:

```clojure
(def rate-limiter (atom {}))

(defn rate-limit [env identifier limit-per-minute]
  (let [now (System/currentTimeMillis)
        minute-ago (- now 60000)
        requests (get @rate-limiter identifier [])
        recent-requests (filter #(> % minute-ago) requests)]
    (if (>= (count recent-requests) limit-per-minute)
      (throw (ex-info "Rate limit exceeded" {:type :rate-limit}))
      (swap! rate-limiter assoc identifier (conj recent-requests now)))))

(pc/defmutation sensitive-operation [env params]
  {::pc/sym `sensitive-operation}
  (rate-limit env (get-client-ip env) 5) ; 5 requests per minute
  ;; Proceed with operation
  )
```

## Logging and Monitoring

### Security Event Logging
```clojure
(defn log-security-event [event-type user-id details]
  (log/warn "Security event"
            {:event-type event-type
             :user-id user-id
             :timestamp (System/currentTimeMillis)
             :details details}))

;; Log authentication failures
(defn authenticate-user [credentials]
  (if-let [user (valid-credentials? credentials)]
    user
    (do
      (log-security-event :auth-failure 
                          (:username credentials)
                          {:ip (get-client-ip)})
      nil)))

;; Log privilege escalation attempts
(defn authorize-admin-action [env]
  (let [user (get-current-user env)]
    (when-not (admin? user)
      (log-security-event :privilege-escalation
                          (:user/id user)
                          {:action "admin-panel-access"})
      (throw (ex-info "Forbidden" {:type :forbidden})))))
```

## Environment-Specific Security

### Development vs Production
```clojure
;; Development debugging (never in production)
(defn debug-middleware [handler]
  (fn [request]
    (if (= :development (:env config))
      (try
        (handler request)
        (catch Exception e
          {:status 500
           :body {:error (str e)
                  :stack-trace (str (.getStackTrace e))}}))
      (handler request))))

;; Production error handling
(defn production-error-handler [handler]
  (fn [request]
    (try
      (handler request)
      (catch Exception e
        (log/error e "Request failed")
        {:status 500
         :body {:error "Internal server error"}}))))
```

### Configuration Security
```clojure
;; Secure configuration management
(def config
  {:database-url (System/getenv "DATABASE_URL")
   :jwt-secret (System/getenv "JWT_SECRET")
   :encryption-key (System/getenv "ENCRYPTION_KEY")
   ;; Never hardcode secrets in source code
   })

;; Validate required environment variables
(defn validate-config []
  (when-not (:jwt-secret config)
    (throw (ex-info "JWT_SECRET environment variable required" {})))
  (when-not (:database-url config)
    (throw (ex-info "DATABASE_URL environment variable required" {}))))
```

## Best Practices

### Security Checklist
- ✅ **Authentication**: Verify user identity
- ✅ **Authorization**: Check user permissions
- ✅ **Input validation**: Validate all inputs
- ✅ **Output encoding**: Prevent XSS attacks
- ✅ **CSRF protection**: Prevent cross-site requests
- ✅ **Session security**: Secure session management
- ✅ **HTTPS**: Encrypt data in transit
- ✅ **Error handling**: Don't leak sensitive information
- ✅ **Logging**: Track security events
- ✅ **Rate limiting**: Prevent abuse

### Development Guidelines
- **Never trust client data**: Validate everything server-side
- **Principle of least privilege**: Minimum necessary access
- **Defense in depth**: Multiple layers of security
- **Fail securely**: Default to denial of access
- **Keep secrets out of code**: Use environment variables
- **Regular security reviews**: Audit code and dependencies
- **Stay updated**: Keep dependencies current with security patches