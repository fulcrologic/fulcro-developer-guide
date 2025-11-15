
# Transactions and Mutations

## Transaction System Overview

### Purpose
Every change to the application database goes through a transaction processing system with two goals:
1. **Abstract the operation** (like a function)
2. **Treat operation as data** (enables remote interactions)

### Transaction Structure
Transactions are vectors of mutation invocations, written as data:
```clojure
(comp/transact! this `[(ops/delete-person {:list-name "Friends" :person "Fred"})])
```

With local variables:
```clojure
(comp/transact! this `[(ops/delete-person {:list-name ~name :person ~person})])
```

## Mutation Definition

### Basic Structure
```clojure
(defmutation delete-person
  "Mutation: Delete person from list"
  [{:keys [list-name name]}]
  (action [{:keys [state]}]
    (let [path (if (= "Friends" list-name)
                 [:friends :list/people]
                 [:enemies :list/people])
          old-list (get-in @state path)
          new-list (vec (filter #(not= (:person/name %) name) old-list))]
      (swap! state assoc-in path new-list))))
```

### Mutation Sections
- **`action`**: Optimistic local changes to client database (runs immediately before any network activity)
- **`remote`**: How mutation interacts with server (return value determines what is sent to remote)
- **`result-action`**: Optional. Custom handler for network results. Defaults to calling `ok-action` or `error-action`
- **`ok-action`**: Optional. Called when remote result is successful (requires default `result-action`)
- **`error-action`**: Optional. Called when remote result is an error (requires default `result-action`)
- **`refresh`**: Optional. Declares which parts of the query to refresh after mutation completes

### Full-Stack Mutations
```clojure
(defmutation delete-person [{:keys [list-id person-id]}]
  (action [{:keys [state]}]
    (swap! state merge/remove-ident* [:person/id person-id] [:list/id list-id :list/people]))
  (remote [env] true))
```

## Using Mutations in UI

### Two Approaches

**With require (preferred):**
```clojure
(ns app.ui
  (:require [app.mutations :as api]))

(comp/transact! this [(api/delete-person {:list-name name})])
```

**Without require (quoted):**
```clojure
(comp/transact! this `[(app.mutations/delete-person {:list-name ~name})])
```

### Mutation Functions
`defmutation` creates functions that return themselves as data:
```clojure
user=> (app.mutations/delete-person {:name "Joe"})
(app.mutations/delete-person {:name "Joe"})
```

## Normalized Database Operations

### Problem with Tree-Based Mutations
```clojure
;; BAD: Tied to UI tree shape
(defmutation delete-person [{:keys [list-name name]}]
  (action [{:keys [state]}]
    (let [path (if (= "Friends" list-name) [:friends :list/people] [:enemies :list/people])]
      ;; Complex tree manipulation...
```

### Solution: Normalized Operations
```clojure
;; GOOD: Works on normalized tables
(defmutation delete-person [{:keys [list-id person-id]}]
  (action [{:keys [state]}]
    (swap! state merge/remove-ident* [:person/id person-id] [:list/id list-id :list/people])))
```

### Database Normalization Benefits
1. **UI independence**: Mutations don't depend on UI tree shape
2. **Local reasoning**: Operations work on specific entities
3. **Composability**: Same operations work regardless of UI refactoring
4. **Deduplication**: Single source of truth for each entity

## Automatic Normalization

### Component Configuration
```clojure
(defsc Person [this {:person/keys [id name age] :as props}]
  {:query [:person/id :person/name :person/age]
   :ident (fn [] [:person/id (:person/id props)])
   :initial-state (fn [{:keys [id name age]}] {:person/id id :person/name name :person/age age})}
  (dom/li (dom/h5 (str name " (age: " age ")"))))
```

### Normalized Database Structure
```clojure
{:friends [:list/id :friends]
 :enemies [:list/id :enemies]
 :person/id {1 {:person/id 1 :person/name "Sally" :person/age 32}
             2 {:person/id 2 :person/name "Joe" :person/age 22}}
 :list/id {:friends {:list/id :friends 
                     :list/label "Friends" 
                     :list/people [[:person/id 1] [:person/id 2]]}}}
```

## Merge Helpers

### Common Operations
```clojure
;; Remove ident from list
(merge/remove-ident* [:person/id person-id] [:list/id list-id :list/people])

;; Add ident to list
(merge/append-ident* [:person/id person-id] [:list/id list-id :list/people])

;; Replace single reference
(merge/replace-ident* [:person/id person-id] [:current-person])
```

### Merge Functions (with `*`)
- Work against plain maps (for use in `swap!`)
- Have non-`*` versions that work directly on atoms
- Convention: `*` suffix for functions used in mutations

## Server-Side Mutations

### Pathom Mutations
```clojure
(ns app.mutations
  (:require
    [com.wsscode.pathom.connect :as pc]
    [app.resolvers :refer [list-table]]))

(pc/defmutation delete-person [env {list-id :list/id person-id :person/id}]
  {::pc/sym `delete-person}
  (log/info "Deleting person" person-id "from list" list-id)
  (swap! list-table update list-id update :list/people 
    (fn [old-list] (filterv #(not= person-id %) old-list))))
```

### Remote Configuration
```clojure
(defmutation delete-person [{:keys [list-id person-id]}]
  (action [{:keys [state]}]
    ;; Local optimistic update
    (swap! state merge/remove-ident* [:person/id person-id] [:list/id list-id :list/people]))
  (remote [env] true))
```

## Transaction Processing Flow

1. **UI calls `transact!`** with mutation data
2. **Local `action`** runs immediately (optimistic update)
3. **Remote section** determines if/how to send to server
4. **Network request** sent if remote returns truthy value
5. **Result handling** via `ok-action` or `error-action` (if using default `result-action`)
6. **UI refresh** triggered based on `refresh` declaration or automatic re-render

### Advanced Remote Handling
```clojure
(defmutation save-person [params]
  (action [env] ...)
  (remote [env] 
    (-> env
      (m/returning Person)           ; Merge result using Person component
      (m/with-target [:current-user]))) ; Target result to specific location
  (ok-action [{:keys [result]}]
    (log/info "Save successful" result))
  (error-action [{:keys [error]}]
    (log/error "Save failed" error)))
```

### Declarative UI Refresh
<!-- NOTE: refresh is an optional section that declares which parts of the query need to be refreshed after a mutation completes -->
```clojure
(defmutation ping-left [params]
  (action [{:keys [state]}]
    (swap! state update-in [:left/by-id 5 :left/value] inc))
  (refresh [env] [:left/value]))

(defmutation ping-right [params]
  (remote [env]
    (m/returning Right))
  (refresh [env] [:right/value]))
```
