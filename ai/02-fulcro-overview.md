
# Fulcro From 10,000 Feet

## Core Philosophy
Fulcro is a full-stack application programming system centered around **graph-based UI and data management**, working in conjunction with Pathom for server-side data processing.

## Key Concepts

### Graph-Centric Design
- **Graphs and Graph Queries**: Excellent way to generalize data models
- **UI trees as directed graphs**: Easily "fed" from graph queries
- **User operations as transactions**: Modeled as data structures that look like function calls

### Data Normalization
- **Arbitrary server graphs**: Need database-style normalization
- **UI tree repetition**: Same data appears in multiple places
- **Local manipulation**: Requires de-duplication of graph data
- **Composition first**: Seamless composition is key to software sustainability

## Architecture Comparison

### "But Does it Have X?"

#### Hiccup Notation
- **Yes, supported**: Use [Sablono](https://github.com/r0man/sablono) library
- **CLJS-only**: No server-side rendering support
- **Fulcro preference**: Functions/macros for isomorphic rendering

#### Reducers/State Management
- **Graph-centric CQRS**: Mutations as "commands" queued like Redux events
- **Enhanced features**: Auto-normalization, centralized transaction processing
- **UI State Machines**: Actor model with runtime role assignment

#### Subscriptions
- **Not core concern**: Websocket remote makes Meteor-like experience possible
- **Server-side focus**: Main difficulty is server-side subscription management
- **Custom remotes**: Adapt to existing GraphQL infrastructure

#### Server-Side Rendering
- **CLJC support**: Runs in headless JS or Java VM environments
- **DOM independence**: Server DOM methods work without JS engine
- **Isomorphic**: Same code runs client and server

## Server vs UI Schema

### Backend Independence
- **No database requirements**: Works with any backend or no backend
- **Schema flexibility**: Pathom reshapes any schema to match UI needs
- **Storage agnostic**: Backend structure doesn't matter for client

### Graph Query Language (EQL)
```clojure
[:person/name {:person/address [:address/street]}]
```

**Returns:**
```clojure
{:person/name "Joe"
 :person/address {:address/street "111 Main St."}}
```

**To-many results:**
```clojure
{:person/name "Joe"
 :person/address [{:address/street "111 Main St."}
                  {:address/street "345 Center Ave."}]}
```

## Client Database

### Normalized Graph Format
- **Simple structure**: Tables with IDs, edges as vectors `[TABLE ID]`
- **Idents**: Tuples of table and ID that uniquely identify graph nodes
- **Table naming convention**: By Fulcro convention, table names match the ID field keyword (e.g., `:person/id` for person entities)
- **Example normalization**:

```clojure
;; Raw data
{:person/id 1 :person/name "Joe" :person/address {:address/id 42 :address/street "111 Main St."}}

;; Normalized
{:person/id  {1    {:person/id 1 :person/name "Joe" :person/address [:address/id 42]}}
 :address/id {42   {:address/id 42 :address/street "111 Main St."}}}
```

### Benefits
- **Automatic de-duplication**: No out-of-sync copies
- **Centralized caching**: Single source of truth
- **Subgraph reasoning**: Load/manipulate any portion of graph
- **Meteor-style subscriptions**: Easy to implement with normalized data

## UI Components as Graph Nodes

### Component-Driven Normalization
- **Co-located metadata**: Query, ident, and initial state with components
- **Ident generation**: The `:ident` metadata generates correct foreign key references

```clojure
;; Using a function (most common for non-trivial cases)
(defn person-ident [props] [:person/id (:person/id props)])

;; Or using shorthand for simple cases (when table name equals ID field keyword)
:ident :person/id
```

### Example Component
```clojure
(defsc Person [this props]
 {:query [:person/id :person/name]
  :ident (fn [] [:person/id (:person/id props)])}
 (dom/div (:person/name props)))
```

Nested components compose automatically - when you use `(comp/get-query Address)` in a parent's query, the data gets normalized according to both components' ident definitions.

## Operational Model

### Mutations
- **Abstract operations**: Like CQRS commands, serializable as data structures
- **UI independence**: UI submits data, doesn't directly manipulate database
- **Multi-section structure**:
  - `action`: Local/optimistic state changes
  - `remote`: Server interaction rules (returns `true` to send mutation, `false` for local-only, or modified mutation)
  - `ok-action`/`error-action`: Network result handling

### Transaction Example
```clojure
(comp/transact! this `[(add-person {:name ~name})])
```

The backtick and unquote (`~`) syntax allows templating mutations with values at the UI layer. This expression is pure data at invocation time.

### Mutation Definition
```clojure
(defmutation add-person [params]
  (action [env] ...)
  (remote [env] ...))
```

An `action` section runs immediately for optimistic updates. A `remote` section determines what (if anything) gets sent to the server.

## Additional Features

### Automatic Benefits
- **Co-located initial state**: Optimized application startup via `:initial-state` in components
- **Query traversal**: Error checking, nested forms, UI router discovery
- **Debugging**: Well-defined state locations, Fulcro Inspect browser extension integration
- **History traversal**: Immutable snapshots enable time travel debugging
- **Development snapshots**: Save/restore application state during development

### UI State Machines
- **Logic organization**: Better than scattered state management - organize related operations around components
- **Reusable patterns**: CRUD operations, login flows, form workflows
- **Component roles**: UI components serve roles within state machines at runtime
- **Centralized debugging**: State stored in normalized database, visible in Fulcro Inspect
