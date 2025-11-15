
# Security

## Overview

Security in Fulcro applications involves both client-side and server-side considerations. However, it's critical to understand that **Fulcro itself is not responsible for authentication or authorization** - these are implemented at the HTTP/middleware level and in your server-side query/mutation parsing logic.

The most critical security concerns are:
1. **Network-level security** (HTTPS, CSRF protection)
2. **Server-side query validation** (authorizing what data clients can request)
3. **Server-side mutation validation** (authorizing what clients can modify)
4. **Sensitive data handling** (never sending secrets to clients)

## Client-Side Security

### Query Security

Client queries should be treated with suspicion on the server - never trust what a client requests. The server must validate and authorize every query:

```clojure
;; Client can request anything, but server controls what's returned
(defsc UserProfile [this {:user/keys [id name email salary]}]
  {:query [:user/id :user/name :user/email :user/salary]}
  (dom/div name " - " email))
```

On the server side, you must implement query validation. The DevelopersGuide recommends checking queries against a whitelist:

```clojure
;; Example of server-side query validation
(def whitelist {:person #{:name :address :email}})

(defn is-authorized-query? [query top-key]
  "Check if requested keywords are in the whitelist for this entity"
  (let [keywords-allowed  (get whitelist top-key #{})
        requested-keywords (keywords-in-query query)]
    (empty? (set/difference requested-keywords keywords-allowed))))
```

The server should reject queries that attempt to access fields the user shouldn't see.

### Sensitive Data Handling

Never store sensitive data in client state:

```clojure
;; BAD: Storing sensitive data where client can access it
{:user/id 123
 :user/name "John"
 :user/password "secret123"     ; Never send to client
 :user/ssn "123-45-6789"        ; Never send to client
 :user/credit-card "4111..."}   ; Never send to client

;; GOOD: Only send necessary client data
{:user/id 123
 :user/name "John"
 :user/email "john@example.com"
 :user/roles #{:user :premium}}
```

### Input Validation

Always validate and sanitize user inputs on both client and server:

```clojure
;; Client-side: Basic formatting (use Fulcro form validation)
(defn validate-user-input [input]
  (-> input
      (update :user/name str/trim)
      (update :user/email str/lower-case)))

;; Note: Always validate again on server - never trust client validation
```

## Server-Side Security

### Authentication

Authentication (verifying who the user is) is handled at the HTTP/middleware level, not by Fulcro. Use standard approaches:

```clojure
;; Your middleware should set user info in the request
(defn authenticated? [request]
  (boolean (get-in request [:session :user/id])))

(defn get-current-user [request]
  (when-let [user-id (get-in request [:session :user/id])]
    (get-user-by-id user-id)))

;; Your server parser has access to the request
(defn parse-mutation [env key params]
  (let [request (:request env)]
    (when-not (authenticated? request)
      (throw (ex-info "Unauthorized" {:status 401})))))
```

### Authorization

Check permissions for specific operations in your server-side query/mutation parsing:

```clojure
;; Example: Authorizing data access in your server parser
(defn is-authorized-root-entity? [request entity-key entity-id]
  "Check if current user can access this entity"
  (let [user (get-current-user request)]
    (cond
      (nil? user) false
      (admin? user) true
      (= (:user/id user) entity-id) true
      :else false)))

;; In your query handler:
(defn handle-person-query [env query params]
  (let [request (:request env)
        person-id (:id params)]
    (when-not (is-authorized-root-entity? request :person person-id)
      (throw (ex-info "Forbidden" {:status 403})))
    ;; Return only authorized fields
    (select-keys (get-user-data person-id) [:user/id :user/name :user/email])))
```

### Input Sanitization

Server-side input validation and sanitization is critical:

```clojure
(defn validate-email [email]
  (and (string? email)
       (re-matches #".+@.+\..+" email)
       (< (count email) 255)))

(defn validate-user-bio [bio]
  "Validate content length and format"
  (and (string? bio)
       (< (count bio) 5000)))

;; In your mutation handler
(defn handle-update-user-bio [env {:user/keys [id bio]}]
  (let [request (:request env)
        user (get-current-user request)]
    (when-not (= (:user/id user) id)
      (throw (ex-info "Forbidden" {:status 403})))
    (when-not (validate-user-bio bio)
      (throw (ex-info "Invalid content" {:status 400})))
    ;; Sanitize HTML if needed - use a library like jsoup or similar
    (update-user-in-db id {:bio bio})))
```

## CSRF Protection

<!-- TODO: Verify this claim -->

### Server-Side CSRF Middleware

The DevelopersGuide emphasizes that you should use `ring.middleware.anti-forgery` with proper configuration:

```clojure
(ns app.middleware.csrf
  (:require [ring.middleware.anti-forgery :refer [wrap-anti-forgery]]
            [ring.middleware.defaults :refer [wrap-defaults]]))

;; Use ring-defaults which includes CSRF protection
(def app-middleware
  (-> handler
      (wrap-defaults {:security {:anti-forgery true
                                 :hsts true
                                 :ssl-redirect true
                                 :frame-options :deny}
                      :session {:cookie-attrs {:http-only true
                                              :secure true
                                              :same-site :strict}}})
      ;; other middleware
      ))
```

### Client-Side CSRF Token Handling

The CSRF token must be added to request **headers**, not the request body:

```clojure
;; Embed token in HTML (recommended approach per DevelopersGuide)
;; In your server's index.html generation:
(str "var fulcro_network_csrf_token = '" anti-forgery-token "';")

;; Client-side middleware to add CSRF header
(defn add-csrf-token [request]
  (let [csrf-token (or js/fulcro_network_csrf_token "TOKEN-NOT-SET")]
    (assoc-in request [:headers "X-CSRF-Token"] csrf-token)))

;; Use in your remote configuration
(def remote-with-csrf
  (http/fulcro-http-remote
    {:url "/api"
     :request-middleware [add-csrf-token]}))
```

**Important**: The CSRF token must be in the HTTP header, not in the request payload. If you can see the token in payload data that only requires a session cookie, your security is broken.

## Session Management

### Secure Session Configuration

Use Ring's session middleware with secure options:

```clojure
(def session-config
  {:store (memory-store)
   :cookie-attrs {:http-only true      ; Prevent JavaScript access
                  :secure true          ; HTTPS only
                  :same-site :strict    ; CSRF protection
                  :max-age 3600}})      ; 1 hour expiry

(def app
  (-> handler
      (wrap-session session-config)))
```

### Session Validation

In your server request handlers, verify session validity:

```clojure
(defn validate-session [request]
  (let [session (:session request)
        user-id (:user/id session)]
    ;; At minimum, verify user is logged in
    (boolean user-id)))

;; Use in your parser
(defn authenticated-query [env query params]
  (when-not (validate-session (:request env))
    (throw (ex-info "Unauthorized" {:status 401})))
  ;; Continue processing...
  )
```

## Data Access Patterns

### Principle of Least Privilege

Only return data the user needs and is authorized to see:

```clojure
;; When loading multiple users, return only appropriate fields
(defn get-user-list [request]
  (let [user (get-current-user request)
        base-fields [:user/id :user/name]]
    {:users (map (fn [user-data]
                   (if (admin? user)
                     ;; Admins see all public fields
                     (select-keys user-data [:user/id :user/name :user/email])
                     ;; Regular users see only names
                     (select-keys user-data base-fields)))
                 (get-all-users))}))
```

### Field-Level Security

Control which fields are visible based on user permissions:

```clojure
;; Example: Different views based on authorization
(defn get-user-data [request user-id]
  (let [current-user (get-current-user request)
        user-data (get-user-by-id user-id)
        public-fields [:user/name]
        private-fields [:user/email :user/phone :user/address]]
    (cond
      ;; User can see their own data
      (= (:user/id current-user) user-id)
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
(defn secure-error-response [error request]
  (let [error-type (:type (ex-data error))
        public-message (case error-type
                        :unauthorized "Access denied"
                        :forbidden "Insufficient permissions"
                        :validation-error "Invalid input"
                        :not-found "Resource not found"
                        "An error occurred")]
    ;; Log detailed error for debugging
    (log/error error "Request error")
    
    ;; Return generic message to client
    {:status 400
     :body {:error public-message}}))
```

### Rate Limiting

Implement rate limiting at the web server or middleware level (e.g., nginx, WAF) or in your application:

```clojure
;; Basic rate limiting per IP/user
;; Consider using a library like clj-rate-limit or redis-based solution for production
(def rate-limiter (atom {}))

(defn check-rate-limit [identifier limit-per-minute]
  (let [now (System/currentTimeMillis)
        minute-ago (- now 60000)
        requests (get @rate-limiter identifier [])
        recent (filter #(> % minute-ago) requests)]
    (if (>= (count recent) limit-per-minute)
      (throw (ex-info "Rate limit exceeded" {:status 429}))
      (swap! rate-limiter assoc identifier (conj recent now)))))

;; Use in critical operations
(defn handle-sensitive-mutation [env params]
  (let [request (:request env)
        client-ip (get-client-ip request)]
    (check-rate-limit client-ip 5) ; 5 requests per minute
    ;; Process mutation...
    ))
```

## Logging and Monitoring

### Security Event Logging

Log security-relevant events for audit purposes:

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
                         {:reason "invalid-credentials"})
      nil)))

;; Log authorization failures
(defn authorize-operation [request operation resource-id]
  (let [user (get-current-user request)]
    (when-not (has-permission? user operation resource-id)
      (log-security-event :auth-denied
                         (:user/id user)
                         {:operation operation :resource resource-id})
      (throw (ex-info "Forbidden" {:status 403})))))
```

## Environment-Specific Security

### Development vs Production

Development and production should have different configurations:

```clojure
;; Keep development loose for easier debugging
(def dev-config
  {:ssl-redirect false          ; Can test without HTTPS
   :anti-forgery false          ; Easier testing
   :verbose-errors true})       ; Show detailed errors

;; Production must be secure
(def prod-config
  {:ssl-redirect true           ; Enforce HTTPS
   :anti-forgery true           ; Enforce CSRF protection
   :verbose-errors false        ; Generic error messages
   :session-timeout 3600})      ; 1 hour

;; Middleware that handles this
(defn create-middleware [env-type]
  (let [config (if (= env-type :production) prod-config dev-config)]
    (-> handler
        (wrap-defaults config))))
```

### Configuration Security

**Never hardcode secrets in your source code.** Use environment variables:

```clojure
;; Secure configuration management
(def config
  {:database-url (System/getenv "DATABASE_URL")
   :jwt-secret (System/getenv "JWT_SECRET")
   :encryption-key (System/getenv "ENCRYPTION_KEY")
   ;; Never hardcode secrets
   })

;; Validate required environment variables on startup
(defn validate-config []
  (when-not (:jwt-secret config)
    (throw (ex-info "JWT_SECRET environment variable required" {})))
  (when-not (:database-url config)
    (throw (ex-info "DATABASE_URL environment variable required" {}))))

;; Call on application startup
(validate-config)
```

## HTTPS and Network Security

Per the DevelopersGuide:

- **Always use HTTPS** in production. Use a reverse proxy like nginx in front of your application.
- **Use secure cookies**: Set `secure: true` on cookies so they're only sent over HTTPS.
- **Set proper headers**: Use middleware like `ring.middleware.defaults` to set security headers:
  - `X-Frame-Options: DENY` (prevent clickjacking)
  - `X-Content-Type-Options: nosniff` (prevent MIME sniffing)
  - `X-XSS-Protection` (browser XSS protection)
  - `Strict-Transport-Security` (enforce HTTPS)

## Best Practices

### Security Checklist

- ✓ **HTTPS**: All authenticated access uses HTTPS (nginx/reverse proxy)
- ✓ **CSRF Protection**: CSRF tokens in headers, validated by middleware
- ✓ **Authentication**: Implemented at middleware/session level
- ✓ **Authorization**: All queries and mutations validated on the server
- ✓ **Input validation**: All inputs validated and sanitized server-side
- ✓ **Output encoding**: Sensitive data never sent to client
- ✓ **Session security**: Secure cookies, proper expiration, validation
- ✓ **Error handling**: Generic error messages to clients
- ✓ **Logging**: Security events logged for audit
- ✓ **Environment config**: Secrets in environment variables only

### Development Guidelines

- **Never trust client data**: Validate and authorize everything server-side
- **Principle of least privilege**: Clients only get data they need
- **Defense in depth**: Multiple layers (HTTPS, CSRF, auth, query validation)
- **Fail securely**: Default to denying access, explicitly grant permissions
- **Keep secrets out of code**: Use environment variables for sensitive values
- **Query whitelisting**: Maintain a whitelist of allowed fields per entity type
- **Regular security reviews**: Audit your query validation, authorization logic
- **Stay updated**: Keep dependencies current with security patches
- **Review the DevelopersGuide**: The official guide has additional security recommendations in section "Security"
