
# Full Stack Operation in Fulcro

## Overview

Fulcro's full-stack operation model unifies server interaction into a clean, data-driven structure. Once you understand the core primitives (component-based queries, idents, and normalization), server interactions become straightforward and predictable.

## Core Benefits

- **Unified Model**: Same data-driven approach for all server interactions
- **Pre-built Middleware**: Fulcro handles networking plumbing automatically
- **Sequential Processing**: Requests are processed in order by default for predictable behavior
- **Automatic Normalization**: Server responses are automatically normalized into the client database
- **Extensible Protocol**: EDN/Transit on the wire with easy type extensions

## Network Protocol Features

### Built-in Capabilities

- **EDN on the Wire**: Uses Transit encoding, allowing pure Clojure data communication
- **Sequential Processing**: All requests processed in order unless specified otherwise
- **Error Handling**: Global and mutation-local error handling options
- **UI Namespace Elision**: `:ui/` namespaced query elements automatically removed from server queries
- **Multiple Remotes**: Support for any number of remote endpoints
- **Protocol Abstraction**: Remotes can be anything (server APIs, local storage, databases)

### Network Request Characteristics

```clojure
;; Example: UI query with mixed concerns
[:user/id :user/name :ui/selected? {:user/posts (comp/get-query Post)}]

;; Automatically becomes server query (ui/ elided)
[:user/id :user/name {:user/posts (comp/get-query Post)}]
```

## Mutations Use Symbols, Not Keywords!

**CRITICAL**: In EQL, mutations are represented as **symbols**, not keywords. This is different from queries, which use keywords.

```clojure
;; ❌ WRONG: Using a keyword
[(create-person {:name "Alice"})]  ; This looks wrong and won't work!

;; ✅ CORRECT: Using a symbol (unquoted in transaction context)
(comp/transact! this [(create-person {:name "Alice"})])

;; Server response is also keyed by SYMBOL
{create-person {:person/id 42 :person/name "Alice"}}
;  ^\
;  └─ Symbol as key, not :create-person keyword!
```

## Types of Server Interactions

### 1. Initial Loads
Application startup data loading

### 2. Incremental Loads
Sub-graphs of previously loaded data

### 3. Event-based Loads
User interactions or timer-triggered data fetching

### 4. External Data Integration
Server push, WebSocket data, third-party APIs

### 5. Remote Operations
Server-side mutations with optional data responses

## Targeting and Nested UI

### The Three-Element Rule

Because Fulcro normalizes all data into a flat graph database, targeting NEVER requires deep paths. Maximum depth is 3 elements: `[table-name id field]`

```clojure
;; Nested UI structure (arbitrarily deep)
Root → MainPanel → UserProfile → FriendsList → Person

;; ❌ You DON'T need deep paths:
{:target [:component/id :root :main-panel :user-profile :friends-list :friends]}

;; ✅ You only need this:
{:target [:component/id :friends-list :friends]}

;; Why? Normalization flattens everything!
```

### Constant Idents for Panels

Components can have constant idents (singletons/panels), making them perfect load targets:

```clojure
(defsc FriendsList [this {:keys [friends]}]
  {:query [{:friends (comp/get-query Person)}]
   :ident (fn [] [:component/id :friends-list])}  ; Constant ident!
  ...)

;; ❌ WRONG: This writes to ROOT, not to component
(df/load! this :friends Person)
;; Result: {:friends [...]} at ROOT

;; ✅ CORRECT: Use explicit :target
(df/load! this :friends Person
  {:target [:component/id :friends-list :friends]})
;; Now the idents are placed at the component's location
```

## Data Fetch API Basics

### df/load! Function Signature

```clojure
(df/load! component-or-app query-key-or-ident component-class optional-params-map)

;; Key optional parameters:
:target                  ; Where to place result: [:table-key id :field]
:params                  ; Server params: {:search "text"}
:marker                  ; Loading indicator: :loading or [:ident/id :field]
:post-mutation          ; Symbol (quoted): `my-mutation - runs after load succeeds
:post-mutation-params   ; Map passed to post-mutation
:fallback               ; Symbol (quoted): `my-fallback - runs on error
:parallel               ; Boolean: if true, load independently (default false)
```

### Basic Load Examples

```clojure
;; Load a list of people to root of database
(df/load! this :people Person)

;; Load with explicit targeting
(df/load! this :people Person
  {:target [:component/id :people-list :items]})

;; Load with server parameters
(df/load! this :autocomplete/results User
  {:params {:search "alice"}
   :target (autocomplete-ident search-id)})

;; Load with post-mutation
(df/load! this :users User
  {:target [:users/list :items]
   :post-mutation `users-loaded})

;; Load with error fallback
(df/load! this :data SomeComponent
  {:fallback `load-failed})
```

## Universal Data Integration Pattern

### The Core Secret

All external data integration uses the same mechanism: **normalization via queries**.

### Standard Flow

```
Query → Server → Response + Original Query → Normalized Data → Database Merge → New Database
```

### External Data Flow

```
External Data + Query → Normalized Data → Database Merge → New Database
```

### Merging with merge-component!

For merging outside of mutations:

```clojure
;; Basic merge-component! usage
(merge/merge-component! app Component data-tree operation path)

;; Example: Merge a new counter to a list
(merge/merge-component! app Counter 
  {:counter/id 4 :counter/n 22}
  :append [:panels/by-kw :counter :counters])

;; Operations: :append, :prepend, :replace
```

### Merging within Mutations

Use `swap!` with the merge-component function:

```clojure
(defmutation create-item [item-data]
  (action [{:keys [state ref]}]
    ;; Merge item into items list, appending to collection
    (swap! state merge/merge-component Item item-data 
      :append [::all-items])
    ;; Can chain multiple updates
    (swap! state assoc-in ref :ui/creating? false)))
```

### Merging Tree Data with Query

For more complex scenarios where you have tree data matching a specific query structure:

```clojure
(defmutation update-local-data [new-data]
  (action [{:keys [state]}]
    ;; state is an atom, use swap!
    (swap! state (fn [db]
      (merge/merge* db new-data 
        [{:updated-items (comp/get-query Item)}])))))
```

## Query Mismatch Resolution

### The Challenge

UI needs may not match server data structure directly.

Example: "All people who've had a particular phone number"

### Resolution Approaches

1. **Query Parser on Server** (Preferred)
   - Use Pathom or similar to resolve UI queries on server
   - Client remains unaware of server schema
   - Server team handles data assembly

2. **Well-known Root Keywords**
   - Invent specific query keys for complex views
   - Hand-code server logic for UI-centric views

3. **Client-side Morphing**
   - Request data in server's natural format
   - Transform on client to match UI needs

### Example: Server Query Resolution

```clojure
;; Client sends UI-based query
[{:people-by-phone (comp/get-query Person)}]

;; Server (with Pathom) resolves to actual data structure
;; Returns tree matching the query shape
{:people-by-phone [{:person/id 1 :person/name "Alice"}
                   {:person/id 2 :person/name "Bob"}]}
```

## Server Interaction Order

### Default Sequential Processing

- Requests are serialized unless marked as parallel
- Events are processed in chronological order
- Prevents out-of-order server execution issues

### Request Batching Rules

```clojure
;; Single event - may be batched together
(defn handle-click [this]
  (df/load! this :users User)  ; Request 1
  (df/load! this :posts Post)) ; Request 2 - may batch with Request 1

;; Separate events - guaranteed sequential
(defn handle-first-click [this]
  (df/load! this :users User))  ; Request 1

(defn handle-second-click [this] 
  (df/load! this :posts Post))  ; Request 2 - waits for Request 1
```

### Write-before-Read Optimization

Fulcro automatically reorders writes before reads in the same processing event:

```clojure
;; This transaction
[(create-user {:name "Alice"})
 (df/load! :users User)]

;; Becomes: create-user first, then load users
;; Ensures the load sees the newly created user
```

### Parallel Override

```clojure
;; Force parallel processing
(df/load! this :users User {:parallel true})
(comp/transact! this [(some-mutation)] {:parallel? true})
```

## Server Result Merging

### The Merging Challenge

Different views of the same entity may have different query depths:

```clojure
;; List view query (minimal)
[:person/id :person/name {:person/image (comp/get-query Image)}]

;; Detail view query (comprehensive)  
[:person/id :person/name :person/age :person/address 
 {:person/phones (comp/get-query Phone)}
 {:person/image (comp/get-query Image)}]
```

### Intelligent Merge Algorithm

Fulcro uses an advanced merging algorithm:

1. **Deep Merge**: Target table entry is updated, not overwritten
2. **Requested but Missing**: If query asks for a value but result doesn't contain it, the value is removed
3. **Not Requested**: If query didn't ask for a value, existing database value is untouched

### Example Merge Behavior

```clojure
;; Current database state
{:person/id {1 {:person/id 1 
                :person/name "Alice" 
                :person/age 30
                :person/phone "555-1234"}}}

;; List refresh query: [:person/id :person/name]
;; Server response: {:person/id 1 :person/name "Alice Smith"}

;; Result after merge
{:person/id {1 {:person/id 1 
                :person/name "Alice Smith"  ; Updated
                :person/age 30             ; Preserved (not in query)
                :person/phone "555-1234"}}} ; Preserved (not in query)
```

### Staleness Considerations

The merge algorithm can create states that never existed on the server:

- **Pro**: Better user experience than data disappearing
- **Con**: Potential for showing outdated information
- **Solution**: Re-load entities when entering edit mode

## Mutations: Local and Remote

### Mutation Definition Blocks

```clojure
(defmutation my-mutation [params]
  ;; action: Local optimistic update (runs immediately)
  (action [{:keys [state app ref]}]
    (swap! state update-in [:table :id] merge new-data))
  
  ;; remote: What to send to server (true = send mutation as-is, false = don't send)
  (remote [env] true)
  
  ;; ok-action: Runs after successful server response
  (ok-action [{:keys [state app ref result]}]
    (log/info "Success:" result))
  
  ;; error-action: Runs on server error
  (error-action [{:keys [state app ref result]}]
    (log/error "Failed:" result))
  
  ;; refresh: Fields to re-query after mutation (vector of keywords)
  (refresh [env] [:my/field :other/field]))
```

### Mutation Environment Variables

Each block receives an environment map with:

- **state**: Atom containing app database (dereference with `@state`)
- **app**: Fulcro application instance
- **ref**: Component's ident path `[:table :id]`
- **result**: Server response (in `ok-action` and `error-action`)
- **ast**: EQL query AST (in `remote` block)

### Remote Block Capabilities

The `remote` block can transform mutations before sending:

```clojure
(defmutation create-entity [{:keys [where?] :as params}]
  (remote [{:keys [ast ref state] :as env}]
    (let [path-to-target (conj ref :children)]
      (cond-> (-> env
              (m/returning Entity)               ; Declare response component type
              (m/with-params (dissoc params where?))) ; Modify params
        (= :append where?) (m/with-target (targeting/append-to path-to-target))
        (= :prepend where?) (m/with-target (targeting/prepend-to path-to-target))))))

;; Key functions:
;; m/returning Component      - Component class for response normalization
;; m/with-target path         - Where to place response in app state
;; m/with-params params-map   - Params to send to server
;; targeting/append-to path   - Append response to collection
;; targeting/prepend-to path  - Prepend to collection
;; targeting/replace-at path  - Replace at specific location
```

## Error Handling

### Default Error Detection

```clojure
;; Default: HTTP status code based
;; 200 = success, anything else = error
```

### Custom Error Detection

```clojure
(def app 
  (app/fulcro-app 
    {:remote-error? (fn [{:keys [body status-code] :as result}] 
                      (or (not= 200 status-code)
                          (contains? body :error)))}))
```

### Error Handling Levels

1. **Global Error Handling**: Application-wide error processing
2. **Mutation Error Handling**: Per-mutation `error-action` and `ok-action`
3. **Load Error Handling**: Specific error handling for data loads

### Load Error with Fallback

```clojure
;; Fallback mutation receives error info in params
(defmutation read-error [params]
  (action [env]
    (let [{:keys [result load-params]} params]
      (js/alert "Load failed!")
      (log/info "Error:" result)
      (log/info "Original load options:" load-params))))

;; Use in load with :fallback option
(df/load! this :data SomeComponent
  {:fallback `read-error})
```

### Mutation Error Handling

```clojure
(defmutation risky-operation [params]
  (action [{:keys [state]}]
    ;; Optimistic update
    (swap! state assoc-in [:ui :pending?] true))
  
  (ok-action [{:keys [state result]}]
    ;; Handle success
    (swap! state assoc-in [:ui :pending?] false))
  
  (error-action [{:keys [app ref result]}]
    ;; Handle error - result contains error details
    (js/alert (str "Operation failed: " result)))
  
  (remote [env] true))
```

## User-Triggered Loading

### Best Practices

**IMPORTANT**: Do not couple loads to React lifecycle methods (like `componentDidMount`, `useEffect`, etc.). Logic should not be tied to UI lifecycle. Instead:

- **User Events**: Trigger loads in response to button clicks, selections, etc.
- **Mutations**: Encapsulate business logic (including conditional loading) in mutations
- **State Machines**: Use state machines or state charts for complex loading workflows

### Recommended Pattern

```clojure
;; ❌ DON'T: Loads in lifecycle methods
(defsc MyComponent [this props]
  {:componentDidMount (fn [this] (df/load! this :data SomeComponent))}
  ...)

;; ✅ DO: User-triggered loads
(defsc MyComponent [this {:keys [data]}]
  {:query [{:data (comp/get-query SomeComponent)}]
   :ident (fn [] [:component/id :my-component])}

  (dom/div
    (if (seq data)
      (ui-some-component data)
      (dom/button
        {:onClick #(df/load! this :data SomeComponent
                     {:target [:component/id :my-component :data]})}
        "Load Data"))))

;; ✅ DO: Mutation-based conditional loading
(defsc PersonRow [this {:person/keys [id name]}]
  {:query [:person/id :person/name]
   :ident :person/id}

  (dom/div
    {:onClick #(comp/transact! this [(select-person {:person/id id})])}
    name))

(defmutation select-person [{:person/id id}]
  (action [{:keys [state app]}]
    ;; Update current selection
    (swap! state assoc-in [:component/id :main-panel :current-person]
      [:person/id id])
    ;; Conditionally load details if missing
    (let [person (get-in @state [:person/id id])
          needs-details? (not (contains? person :person/age))]
      (when needs-details?
        (df/load! app [:person/id id] PersonDetail))))
  (remote [_] false))
```

## Practical Examples

### Simple Data Load

```clojure
(defn load-users [this]
  (df/load! this :users User
    {:target [:users/list]
     :post-mutation `users-loaded}))
```

### Server Push Integration

```clojure
(defn handle-websocket-message [app message]
  (case (:type message)
    :user-update 
    (merge/merge-component! app User (:user message))
    
    :new-notification
    (merge/merge-component! app Notification (:notification message))
    
    :unknown nil))
```

### Complex Full-Stack Operation

```clojure
(defmutation create-and-load-user [user-params]
  (action [{:keys [state]}]
    ;; Optimistic update
    (merge/merge-component* state User 
                           (merge user-params {:ui/creating? true})))
  (remote [env]
    ;; Server mutation
    (m/returning env User))
  (ok-action [{:keys [state result]}]
    ;; Handle server response
    (let [new-user (User result)]
      (swap! state merge/merge-component User new-user 
        :append [:users/list]))))
```

## Summary

This unified approach to full-stack operation makes Fulcro applications predictable, testable, and maintainable while handling the complexities of distributed systems transparently.
