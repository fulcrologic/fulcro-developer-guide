# Building a Fulcro Server

## Overview

Building a Fulcro server is relatively straightforward - most complexity comes from setup for development workflow (component restart, configuration) rather than API handling itself. The server primarily processes EQL queries from clients and returns matching data trees.

## Basic Requirements

### Essential Components

1. **Ring Stack with Transit**: Must include `transit-response` and `transit-params` middleware
2. **API Handler**: Use `api-middleware/handle-api-request` for the API endpoint (defaults to `/api`)
3. **EQL Parser**: Pathom is recommended for processing client queries

### Minimal Ring Stack Example

```clojure
(ns my-app.server
  (:require 
    [ring.middleware.transit :refer [wrap-transit-response wrap-transit-params]]
    [com.fulcrologic.fulcro.server.api-middleware :as api-middleware]))

(def middleware-stack
  (-> handler
      wrap-transit-response
      wrap-transit-params
      (api-middleware/wrap-api {:uri "/api"
                                :parser my-parser})))
```

## Using Pathom for EQL Processing

### Why Pathom?

While you can hand-write a server-side parser for EQL, **Pathom is strongly recommended** because it:

- Automatically resolves graph queries
- Handles data dependencies intelligently  
- Provides excellent tooling and debugging
- Scales well with complex data requirements

### Basic Pathom Integration

```clojure
(ns my-app.parser
  (:require 
    [com.wsscode.pathom3.connect.operation :as pco]
    [com.wsscode.pathom3.interface.eql :as p.eql]))

;; Define resolvers
(pco/defresolver user-by-id [{:keys [user/id]}]
  {::pco/output [:user/name :user/email]}
  {:user/name "Alice" :user/email "alice@example.com"})

(pco/defresolver all-users [env input]
  {::pco/output [{:all-users [:user/id]}]}
  {:all-users [{:user/id 1} {:user/id 2}]})

;; Create parser  
(def parser
  (p.eql/boundary-interface
    {::pco/indexes (pco/register [user-by-id all-users])}))

;; Use in API handler
(defn api-handler [request]
  (api-middleware/handle-api-request parser request))
```

For comprehensive Pathom documentation, see the [Pathom Developer's Guide](https://wilkerlucio.github.io/pathom).

## Server Configuration

### Configuration System

Fulcro provides configuration utilities in `com.fulcrologic.fulcro.server.config` namespace.

### Required Files

1. **`config/defaults.edn`** (from CLASSPATH/resources)
   - Contains default configuration map
   - Baseline settings for all environments

2. **Environment-specific config file**
   - Machine-local overrides
   - Can be anywhere (you specify the path)
   - Required (prevents accidental production deploys without config)

### Configuration File Structure

**`config/defaults.edn`:**
```clojure
{:database {:host "localhost"
            :port 5432
            :name "myapp_dev"}
 :server   {:port 3000
            :host "0.0.0.0"}
 :auth     {:secret "dev-secret"}}
```

**`/etc/myapp/production.edn`:**
```clojure
{:database {:host "prod-db.example.com"
            :name "myapp_production"}
 :auth     {:secret :env/AUTH_SECRET}}
```

### Loading Configuration

```clojure
(ns my-app.server
  (:require [com.fulcrologic.fulcro.server.config :as config]))

;; Basic usage
(defn make-system []
  (let [config (config/load-config! {:config-path "/usr/local/etc/app.edn"})]
    (create-server-components config)))

;; With component systems (like Mount)
(defstate config 
  :start (config/load-config! {:config-path "config/dev.edn"}))
```

### Configuration Features

#### Deep Merge
Environment config recursively merges with defaults:

```clojure
;; defaults.edn
{:database {:host "localhost" :port 5432 :ssl false}}

;; production.edn  
{:database {:host "prod-server" :ssl true}}

;; Result
{:database {:host "prod-server" :port 5432 :ssl true}}
```

#### Environment Variable Support

```clojure
{:database {:password :env/DB_PASSWORD}        ; String value
 :port     :env.edn/SERVER_PORT               ; EDN parsed value
 :debug    :env.edn/DEBUG_MODE}               ; Could be boolean from env
```

#### JVM Override
Override config file via JVM option:
```bash
java -Dconfig=/path/to/prod.edn -jar myapp.jar
```

#### Relative Paths
Relative paths search CLASSPATH (useful for packaged configs):
```clojure
(config/load-config! {:config-path "config/docker.edn"})
```

### Complete Server Example

```clojure
(ns my-app.server
  (:require 
    [mount.core :as mount :refer [defstate]]
    [ring.adapter.jetty :as jetty]
    [com.fulcrologic.fulcro.server.config :as config]
    [com.fulcrologic.fulcro.server.api-middleware :as api]))

;; Configuration
(defstate config
  :start (config/load-config! {:config-path "config/dev.edn"}))

;; Parser (using Pathom)
(defstate parser
  :start (create-pathom-parser config))

;; Ring handler
(defstate handler
  :start (-> (fn [req] {:status 404 :body "Not found"})
             (api/wrap-api {:uri "/api" :parser parser})
             wrap-transit-response
             wrap-transit-params))

;; Web server
(defstate web-server
  :start (jetty/run-jetty handler 
                         {:port (get-in config [:server :port])
                          :join? false})
  :stop (.stop web-server))

;; Startup
(defn -main [& args]
  (mount/start))
```

## Custom Type Support

### Overview

Fulcro 3.3.6+ includes centralized custom type support via a global type registry, eliminating the need to pass transit options throughout your application.

### Why Custom Types?

- **Seamless Data Flow**: Use rich types throughout client/server boundary
- **Type Safety**: Maintain type information across network calls
- **Developer Experience**: No manual serialization/deserialization

### Defining Custom Types

```clojure
(ns my-app.types
  (:require [com.fulcrologic.fulcro.algorithms.transit :as transit]))

;; Define the type
(deftype Point [x y])

;; Install type handler
(defn install-types! []
  (transit/install-type-handler!
    (transit/type-handler Point "geo/point"
      ;; Serialize: type -> representation
      (fn [^Point p] [(.-x p) (.-y p)])
      ;; Deserialize: representation -> type  
      (fn [[x y]] (Point. x y)))))
```

### Nested Custom Types

Custom types can contain other custom types:

```clojure
(deftype Point [x y])
(deftype Rectangle [^Point ul ^Point lr])

(defn install-types! []
  ;; Install Point first
  (transit/install-type-handler!
    (transit/type-handler Point "geo/point"
      (fn [^Point p] [(.-x p) (.-y p)])
      (fn [[x y]] (Point. x y))))
  
  ;; Rectangle can use Point in its representation
  (transit/install-type-handler!
    (transit/type-handler Rectangle "geo/rect"
      (fn [^Rectangle r] [(.-ul r) (.-lr r)])
      (fn [[ul lr]] (Rectangle. ul lr)))))
```

### Real-World Example: Date Handling

```clojure
(ns my-app.types
  (:require 
    [com.fulcrologic.fulcro.algorithms.transit :as transit]
    #?(:clj [java-time :as jt]
       :cljs [js-joda :as jt])))

(defn install-date-types! []
  ;; LocalDate support
  (transit/install-type-handler!
    (transit/type-handler 
      #?(:clj java.time.LocalDate :cljs js-joda.LocalDate)
      "date/local"
      #?(:clj #(str %) :cljs #(.toString %))
      #?(:clj #(jt/local-date %) :cljs #(jt/LocalDate.parse %)))))

;; Call this early in your application startup
(install-date-types!)
```

### Integration Points

Custom types work automatically with:

- **HTTP Remote**: Network requests/responses
- **WebSocket Remote**: Real-time communication (v3.2.0+)
- **Fulcro RAD**: Rapid Application Development tools
- **Fulcro Inspect**: Development tools
- **String Conversion**: `transit/transit-clj->str` utilities

### Installation Timing

**Critical**: Install type handlers as early as possible in your application lifecycle:

```clojure
(ns my-app.main
  (:require [my-app.types :as types]))

;; Do this BEFORE creating client/server instances
(defn init! []
  (types/install-types!)  ; Install first
  (start-client!)         ; Then start app
  (start-server!))
```

### Best Practices

1. **Use `deftype` over `defrecord`**: Records look like maps to Fulcro internals
2. **Install Early**: Before creating any Fulcro instances
3. **Unique Tags**: Use globally unique string tags (e.g., "mycompany/user-id")
4. **Simple Representations**: Use basic data structures Transit already supports
5. **Test Thoroughly**: Custom types in state database need careful testing

## Template Projects

For production applications, consider starting with a template project that includes:

- Pre-configured server setup
- Development restart capability
- Testing infrastructure  
- Build/deployment configuration
- Example Pathom resolvers

This approach saves significant setup time and provides battle-tested patterns for server development.

## Development Workflow

### Component Restart

Use component systems (Mount, Component, Integrant) for easy development restart:

```clojure
;; Mount example
(mount/stop)   ; Stop all components
(mount/start)  ; Restart with new code
```

### Configuration Reloading

Use relative config paths for development:

```clojure
;; Loads from resources/config/dev.edn
(config/load-config! {:config-path "config/dev.edn"})
```

This allows config changes without rebuilding the application.

Building a Fulcro server focuses on data transformation and business logic rather than HTTP plumbing, making it straightforward to create robust, scalable backend services.