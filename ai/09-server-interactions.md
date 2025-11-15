
# Server Interactions

## Overview
Fulcro uses EQL (EDN Query Language) over Transit protocol for all server communication. A single API endpoint processes both queries and mutations. The standard server stack uses Ring middleware with Pathom for parsing EQL queries and mutations.

## Server Setup

### Basic Ring Server
```clojure
(ns app.server
  (:require
    [app.parser :refer [api-parser]]
    [org.httpkit.server :as http]
    [com.fulcrologic.fulcro.server.api-middleware :as server]
    [ring.middleware.content-type :refer [wrap-content-type]]
    [ring.middleware.resource :refer [wrap-resource]]))

(def ^:private not-found-handler
  (fn [req]
    {:status  404
     :headers {"Content-Type" "text/plain"}
     :body    "Not Found"}))

(def middleware
  (-> not-found-handler
    (server/wrap-api {:uri "/api" :parser api-parser})
    (server/wrap-transit-params)
    (server/wrap-transit-response)
    (wrap-resource "public")
    wrap-content-type))

(defonce stop-fn (atom nil))

(defn start []
  (reset! stop-fn (http/run-server middleware {:port 3000})))

(defn stop []
  (when @stop-fn
    (@stop-fn)
    (reset! stop-fn nil)))
```

Note: Ring middleware is composed innermost-first. The `not-found-handler` executes last, and `wrap-content-type` executes first.

### Pathom Parser Setup
```clojure
(ns app.parser
  (:require
    [app.resolvers :refer [resolvers]]
    [com.wsscode.pathom.core :as p]
    [com.wsscode.pathom.connect :as pc]))

(def pathom-parser
  (p/parser {::p/env     {::p/reader [p/map-reader pc/reader2 pc/ident-reader]
                          ::p/placeholder-prefixes #{">"}}
             ::p/mutate  pc/mutate
             ::p/plugins [(pc/connect-plugin {::pc/register resolvers})
                          p/error-handler-plugin
                          p/trace-plugin
                          (p/post-process-parser-plugin p/elide-not-found)]}))

(defn api-parser [query]
  (pathom-parser {} query))
```

The `p/elide-not-found` plugin is important - it removes `::p/not-found` values from results, preventing malformed idents like `[:thing/id nil]` in your Fulcro database.

## Pathom Resolvers

### Basic Resolver Structure
```clojure
(pc/defresolver person-resolver [env {:person/keys [id]}]
  {::pc/input  #{:person/id}      ; Required inputs
   ::pc/output [:person/name :person/age]}  ; Available outputs
  (get people-table id))
```

### Input/Output Declaration
- **`::pc/input`**: Set of required input keys that must be present in the input map
- **`::pc/output`**: EQL describing what keys this resolver can provide
- **Resolution**: Pathom chains resolvers based on input/output relationships to fulfill queries

### Example Resolvers
```clojure
;; Entity resolver - requires :person/id as input
(pc/defresolver person-resolver [env {:person/keys [id]}]
  {::pc/input  #{:person/id}
   ::pc/output [:person/name :person/age]}
  (get people-table id))

;; Collection resolver with to-many relationship
(pc/defresolver list-resolver [env {:list/keys [id]}]
  {::pc/input  #{:list/id}
   ::pc/output [:list/label {:list/people [:person/id]}]}
  (when-let [list (get list-table id)]
    (assoc list :list/people (mapv (fn [id] {:person/id id}) (:list/people list)))))

;; Root resolver (no inputs) - like GraphQL root queries
(pc/defresolver friends-resolver [env input]
  {::pc/output [{:friends [:list/id]}]}
  {:friends {:list/id :friends}})
```

When a resolver cannot provide the promised output, omit that key from the returned map. Pathom will mark it as `::p/not-found`, which gets removed if you use the `p/elide-not-found` plugin.

### Ident Queries
EQL supports ident-based queries for specific entities:
```clojure
[{[:person/id 1] [:person/name]}]
=> {[:person/id 1] {:person/name "Sally"}}
```

Pathom uses the ident to provide context, finding resolvers whose `::pc/input` matches the ident's keys.

## Client Remote Configuration

### HTTP Remote Setup
```clojure
(ns app.application
  (:require
    [com.fulcrologic.fulcro.application :as app]
    [com.fulcrologic.fulcro.networking.http-remote :as http]))

(defonce app (app/fulcro-app
               {:remotes {:remote (http/fulcro-http-remote {})}}))
```

The default remote uses `/api` as the endpoint on the same origin as the page.

### Multiple Remotes
```clojure
(defonce app (app/fulcro-app
               {:remotes {:api    (http/fulcro-http-remote {:url "/api"})
                          :auth   (http/fulcro-http-remote {:url "/auth"})
                          :files  (http/fulcro-http-remote {:url "/files"})}}))
```

## Server Mutations

### Pathom Mutation Definition
```clojure
(ns app.mutations
  (:require 
    [com.wsscode.pathom.connect :as pc]
    [taoensso.timbre :as log]))

(pc/defmutation delete-person [env {list-id :list/id person-id :person/id}]
  {::pc/sym `delete-person}  ; Optional: override the indexed symbol
  (log/info "Deleting person" person-id "from list" list-id)
  (swap! list-table update list-id 
    update :list/people (fn [old-list] 
                          (filterv #(not= person-id %) old-list))))
```

The `::pc/sym` option allows you to override which symbol the mutation is indexed under. If omitted, it uses the fully-qualified symbol from the namespace.

### Mutation Return Values
```clojure
(pc/defmutation save-person [env {:person/keys [id name]}]
  {::pc/sym `save-person}
  (let [saved-person (save-to-database {:person/id id :person/name name})]
    {:person/id id :person/name name :person/updated-at (java.util.Date.)}))
```

Return values from mutations are sent back to the client and can be merged into the app state.

### Joining Mutation Results
Server mutations can return data that Fulcro automatically merges when you use `returning`:

```clojure
;; Client mutation with returning
(defmutation save-person [params]
  (action [env] ...)
  (remote [env] (m/returning env Person)))

;; Server can return minimal data - Pathom resolvers handle the rest
(pc/defmutation save-person [env params]
  {::pc/output [:person/id]}
  {:person/id 1})
```

Because of `::pc/output`, the server mutation only needs to return `:person/id`. Pathom resolvers will then resolve the full `Person` query using that ID.

## Error Handling

### Client Error Handling
```clojure
(defmutation save-data [params]
  (action [env] ...)
  (remote [env] true)
  (error-action [{:keys [error]}]
    (log/error "Save failed:" error)))
```

The `error-action` is called when the remote request fails or returns an error.

### Server Error Responses
<!-- TODO: Verify this claim - need to check standard Pathom error handling patterns -->
Mutations should throw exceptions or return error data. The Pathom error handler plugin will catch and format errors appropriately.

## Query Parameters

### Client-Side Parameters
```clojure
(df/load! this :people Person 
  {:params {:limit 10 :offset 20 :filter "active"}})
```

### Server Parameter Access
Parameters are available in the resolver's `env` under the `:ast :params` path:

```clojure
(pc/defresolver people-resolver [env input]
  {::pc/output [{:people [:person/id]}]}
  (let [{:keys [limit offset filter]} (-> env :ast :params)]
    {:people (query-people :limit limit :offset offset :filter filter)}))
```

## Resolver Dependencies

Pathom automatically chains resolvers based on their input/output declarations:

```clojure
(pc/defresolver user-posts [env {:user/keys [id]}]
  {::pc/input  #{:user/id}
   ::pc/output [{:user/posts [:post/id]}]}
  {:user/posts (get-posts-for-user id)})

(pc/defresolver post-details [env {:post/keys [id]}]
  {::pc/input  #{:post/id}
   ::pc/output [:post/title :post/content]}
  (get-post-details id))

;; Query: [{[:user/id 1] [{:user/posts [:post/title]}]}]
;; Pathom chains: user/id → user/posts → post/title
```

## Development Tools

### Parser Testing
Test your parser directly in a Clojure REPL:

```clojure
(require 'app.parser)

(app.parser/api-parser [{[:person/id 1] [:person/name]}])
=> {[:person/id 1] {:person/name "Sally"}}
```

### Query Tracing
Enable the trace plugin in your parser configuration to debug query resolution:

```clojure
{::p/plugins [...
              p/trace-plugin  ; Add for query tracing
              ...]}
```

### Server Refresh Pattern
```clojure
(ns user
  (:require 
    [app.server :as server]
    [clojure.tools.namespace.repl :refer [refresh]]))

(defn restart []
  (server/stop)
  (refresh :after 'user/start))
```

## Security Considerations

### Query Authorization
Control access within resolvers using the `env`:

```clojure
(pc/defresolver sensitive-data [env input]
  {::pc/output [:sensitive/field]}
  (when (authorized? env)
    {:sensitive/field "secret"}))
```

### Mutation Authorization
```clojure
(pc/defmutation admin-operation [env params]
  {::pc/sym `admin-operation}
  (when-not (admin? env)
    (throw (ex-info "Unauthorized" {:type :unauthorized})))
  (perform-admin-operation params))
```

### Parameter Validation
```clojure
(pc/defmutation create-user [env {:user/keys [name email] :as params}]
  {::pc/sym `create-user}
  (when-not (and (valid-name? name) (valid-email? email))
    (throw (ex-info "Invalid parameters" {:type :validation-error})))
  (create-user-in-db params))
```

The `env` parameter can be augmented during parser setup to include things like the current user, database connection, or request context for authorization and validation.
