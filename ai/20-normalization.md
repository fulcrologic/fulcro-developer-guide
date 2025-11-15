
# Normalization in Fulcro

## Overview

Normalization is a central mechanism in Fulcro that transforms data trees (received from component queries against servers) into a normalized graph database. This process enables efficient data management, prevents duplication, and maintains referential integrity across the application.

## Why Normalization Matters

- **Data Consistency**: Single source of truth for each entity
- **Memory Efficiency**: Eliminates data duplication
- **Update Propagation**: Changes to an entity are automatically reflected everywhere it's referenced
- **Query Optimization**: Enables efficient data fetching and caching

## The Normalization Process

### Core Function: `tree->db`

The function `fnorm/tree->db` is the workhorse that turns an incoming tree of data into normalized data, which can then be merged into the overall database.

**IMPORTANT**: `tree->db` takes a **component class** as its first parameter, not a raw query. The component class provides both the query (with metadata) and the ident function needed for normalization.

### Step-by-Step Process

Given incoming tree data:

```clojure
{:people [{:db/id 1 :person/name "Joe" ...} 
          {:db/id 2 :person/name "Sally" ...}]}
```

And a component:

```clojure
(defsc Person [this props]
  {:query [:db/id :person/name]
   :ident :db/id})

(defsc Root [this props]
  {:query [{:people (comp/get-query Person)}]})
```

When `comp/get-query` is called, it adds metadata to the query:

```clojure
[{:people [:db/id :person/name]}]
          ; ^ metadata {:component Person}
```

The `tree->db` function recursively walks the data structure and query:

1. **Root Processing**: Sees `:people` as a root key and property, remembers it will be writing `:people` to the root
2. **Relationship Detection**: Examines the value of `:people` and finds it to be a vector of maps, indicating a to-many relationship
3. **Component Discovery**: Examines the metadata on the subquery of `:people` and discovers that entries are represented by the component `Person`
4. **Ident Generation**: For each map in the vector, calls the `ident` function of `Person` (found in metadata) to get a database location
5. **Data Placement**: Places the "person" values into the result via `assoc-in` on the ident
6. **Reference Replacement**: Replaces the entries in the vector with the idents

## Graph Database Structure

### Before Normalization (Tree Structure)

```clojure
{:current-user {:user/id 1
                :user/name "Alice"
                :user/friends [{:user/id 2 :user/name "Bob"}
                               {:user/id 3 :user/name "Charlie"}]}
 :all-users [{:user/id 1 :user/name "Alice"}
             {:user/id 2 :user/name "Bob"}
             {:user/id 3 :user/name "Charlie"}]}
```

### After Normalization (Graph Structure)

```clojure
{:user/id {1 {:user/id 1 
              :user/name "Alice"
              :user/friends [[:user/id 2] [:user/id 3]]}
           2 {:user/id 2 :user/name "Bob"}
           3 {:user/id 3 :user/name "Charlie"}}
 :current-user [:user/id 1]
 :all-users [[:user/id 1] [:user/id 2] [:user/id 3]]}
```

## Idents and Entity References

### What are Idents?

Idents are two-element vectors that uniquely identify entities in the normalized database:

```clojure
[:user/id 1]     ; Points to user with ID 1
[:product/sku "ABC123"]  ; Points to product with SKU "ABC123"
[:component :singleton]  ; Points to a singleton component
```

### Component Ident Functions

```clojure
(defsc Person [this props]
  {:ident :person/id  ; Simple keyword ident (most common)
   :query [:person/id :person/name]}
  ...)

(defsc Person [this props]
  {:ident [:person/id :person/id]  ; Template form
   :query [:person/id :person/name]}
  ...)

(defsc Product [this props]
  {:ident (fn [] [:product/sku (:product/sku props)])  ; Lambda form
   :query [:product/sku :product/name]}
  ...)
```

All three forms are supported by `defsc`:
- Keyword form (`:person/id`) - uses the keyword as both table name and property to extract from props
- Template vector form (`[:person/id :person/id]`) - first element is literal table name, second is property key
- Lambda form - fully custom, can close over `this` and `props` parameters

## Critical Importance of Query Composition

### Why Metadata Matters

If metadata is missing from queries, normalization won't occur:

```clojure
;; WRONG - Missing component metadata
[{:people [:db/id :person/name]}]

;; CORRECT - Has component metadata from get-query
[{:people (comp/get-query Person)}]
```

### Parallel Structure Requirement

The query and tree of data must have parallel structure, as should the UI:

```clojure
;; Component structure
(defsc PersonList [this {:keys [people]}]
  {:query [{:people (comp/get-query Person)}]}
  ...)

;; Matching data structure
{:people [{:person/id 1 :person/name "Alice"}
          {:person/id 2 :person/name "Bob"}]}

;; Resulting normalized structure
{:person/id {1 {:person/id 1 :person/name "Alice"}
             2 {:person/id 2 :person/name "Bob"}}
 :people [[:person/id 1] [:person/id 2]]}
```

## Normalization in Different Contexts

### 1. Initial State Normalization

At startup, `:initial-state` supplies data that matches the UI tree structure:

```clojure
(defsc Root [this props]
  {:initial-state (fn [params]
                    {:current-user (comp/get-initial-state User {:id 1 :name "Alice"})
                     :user-list [(comp/get-initial-state User {:id 2 :name "Bob"})]})
   :query [{:current-user (comp/get-query User)}
           {:user-list (comp/get-query User)}]}
  ...)
```

During app initialization, Fulcro calls `tree->db` with the root component class and the initial state tree to normalize it into the client database.

### 2. Server Interaction Normalization

Network interactions send UI-based queries with component annotations:

```clojure
;; Query sent to server (automatically composed from components)
[{:people (comp/get-query Person)}]

;; Response data (tree structure matching query)
{:people [{:person/id 1 :person/name "Alice"}
          {:person/id 2 :person/name "Bob"}]}

;; Automatic normalization and merge into database happens via the
;; component-annotated query
```

### 3. Integrating External Data

For server push data or other external sources, you can normalize using merge functions with component classes:

```clojure
;; Incoming WebSocket data
{:new-message {:message/id 123 :message/text "Hello"}}

;; Use merge-component! to normalize and merge
(merge/merge-component! app Message 
  {:message/id 123 :message/text "Hello"}
  :append [:root/messages])
```

### 4. Mutation Data Normalization

Mutations can normalize new entity data within the action:

```clojure
(defmutation create-user [user-data]
  (action [{:keys [state]}]
    ;; Use merge-component* (the swap! version) in mutations
    (swap! state merge/merge-component* User user-data
      :append [:root/users])))
```

## Useful Normalization Functions

### Merge Functions

```clojure
;; Merge new component instances (app version - triggers render)
(merge/merge-component! app User new-user-data)

;; Merge new component instances (state-map version for mutations)
(merge/merge-component* state User new-user-data)

;; Merge root-level data
(merge/merge! app {:global-settings {...}})
(merge/merge* state {:global-settings {...}})
```

### Core Normalization

```clojure
;; Low-level normalization utility - takes a COMPONENT CLASS
(fnorm/tree->db ComponentClass data-tree include-root?)

;; Example usage
(fnorm/tree->db Root 
  {:users [{:user/id 1 :user/name "Alice"}]}
  true)
```

**Note**: In practice, you'll typically use `merge/merge-component!` and `merge/merge-component*` rather than calling `tree->db` directly. The `tree->db` function has a quirky interface for internal reasons.

### Integration Utilities

```clojure
;; Add ident to existing relationships
(targeting/integrate-ident* state [:user/id 1] :append [:root/users])
(targeting/integrate-ident* state [:user/id 1] :prepend [:user/id 2 :user/friends])
(targeting/integrate-ident* state [:user/id 1] :replace [:root/current-user])
```

## Advanced Options

### Remove Missing Data

The `:remove-missing?` option controls cleanup behavior:

```clojure
(merge/merge-component! app User user-data {:remove-missing? true})
```

When `true`:
- Items in query but not in data are removed from state database
- Useful for server load cleanups where you want to ensure removed server-side data is also removed client-side
- Defaults to `false` to preserve UI-only attributes

### Deep Merge Behavior

The deep merge used by merge routines:
- Merges incoming data into existing entities
- Preserves UI-only attributes that incoming trees don't include
- Maintains data integrity across partial updates by not removing properties that weren't queried

For example, if an entity has `:user/id`, `:user/name`, and `:ui/selected?`, and you merge in data with only `:user/id` and `:user/name`, the `:ui/selected?` property is preserved.

## Best Practices

1. **Always use `comp/get-query`** in parent component queries to ensure proper metadata
2. **Maintain parallel structure** between queries, data, and UI components
3. **Use consistent ident patterns** across your application
4. **Use merge-component! rather than tree->db directly** for better ergonomics
5. **Consider `:remove-missing?`** carefully based on your data update patterns
6. **Test normalization** by examining the resulting database structure in Fulcro Inspect

## Common Patterns

### To-One Relationship

```clojure
;; Component definition
(defsc User [this {:keys [user/profile]}]
  {:query [:user/id {:user/profile (comp/get-query Profile)}]
   :ident :user/id}
  ...)

;; Data structure
{:user/id 1 :user/profile {:profile/id 100 :profile/bio "..."}}

;; Normalized result
{:user/id {1 {:user/id 1 :user/profile [:profile/id 100]}}
 :profile/id {100 {:profile/id 100 :profile/bio "..."}}}
```

### To-Many Relationship

```clojure
;; Component definition
(defsc User [this {:keys [user/posts]}]
  {:query [:user/id {:user/posts (comp/get-query Post)}]
   :ident :user/id}
  ...)

;; Data structure  
{:user/id 1 :user/posts [{:post/id 1 :post/title "First"}
                         {:post/id 2 :post/title "Second"}]}

;; Normalized result
{:user/id {1 {:user/id 1 :user/posts [[:post/id 1] [:post/id 2]]}}
 :post/id {1 {:post/id 1 :post/title "First"}
           2 {:post/id 2 :post/title "Second"}}}
```

Understanding normalization is crucial for effective Fulcro development, as it underlies all data management operations in the framework.
