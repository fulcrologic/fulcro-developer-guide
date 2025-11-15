
# Fulcro Cheat Sheet

## Core Philosophy

Fulcro is built on three fundamental concepts that work together:

1. **Components with Co-located Queries**: Every UI component declares what data it needs via `:query`
2. **Normalized Graph Database**: All data stored in normalized tables with ident-based references
3. **Abstract Graph-Based I/O**: All server interactions use EQL (EDN Query Language)

## The Big Picture: Nested UI + Normalization

### Example: Nested UI Structure

```clojure
Root [:component/id :root]
  ├─ MainPanel [:component/id :main-panel]
  │    ├─ CurrentUser [:user/id 1]
  │    │    └─ Avatar [:user/id 1]
  │    └─ FriendsList [:component/id :friends-list]
  │         └─ Person [:person/id 2]
  │              └─ Person [:person/id 3]
  └─ Sidebar [:component/id :sidebar]
```

**Key Insight**: Each component has an ident. Components with constant idents (like `[:component/id :main-panel]`) are perfect targets for loading subtrees.

### The Normalization Magic

```
┌─────────────────────────────────────────────────────────────┐
│ Step 1: Tree Data from Server                                │
├─────────────────────────────────────────────────────────────┤
│ {:main-panel                                                  │
│   {:current-user {:user/id 1 :user/name "Alice"              │
│                   :user/avatar {:user/id 1 :avatar/url "…"}} │
│    :friends-list {:friends [{:person/id 2 :person/name "Bob"}│
│                             {:person/id 3 :person/name "Eve"}]}}}│
└─────────────────────────────────────────────────────────────┘
                           ↓
┌─────────────────────────────────────────────────────────────┐
│ Step 2: After Normalization (in app state)                   │
├─────────────────────────────────────────────────────────────┤
│ {:component/id                                                │
│   {:root {...}                                                │
│    :main-panel {:current-user [:user/id 1]                   │
│                 :friends-list [:component/id :friends-list]} │
│    :friends-list {:friends [[:person/id 2] [:person/id 3]]}} │
│  :user/id                                                     │
│   {1 {:user/id 1 :user/name "Alice"                          │
│       :user/avatar [:user/id 1]}}                             │
│  :person/id                                                   │
│   {2 {:person/id 2 :person/name "Bob"}                       │
│    3 {:person/id 3 :person/name "Eve"}}}                     │
└─────────────────────────────────────────────────────────────┘
```

### Why Targeting is Simple

Because data is normalized, you NEVER need deep paths. To target any depth:

```clojure
;; ❌ You might think you need this:
[:component/id :root :main-panel :friends-list :friends]

;; ✅ You actually only need this:
[:component/id :friends-list :friends]
;; Why? Because :friends-list is already a normalized reference!

;; Load WITH explicit targeting:
(df/load! app :friends Person
  {:target [:component/id :friends-list :friends]})
;; Normalizes data AND places idents at target location
```

**The Rule**: Maximum targeting depth is 3: `[table-name id field]`

**CRITICAL**: `load!` with a keyword normalizes data into tables but does NOT automatically create edges in the tree. The data won't appear in your UI unless you either (1) use `:target` to place it in the tree, or (2) query it directly from the root. Loading by ident also only normalizes without placing the ident in the tree.

## Components with Idents

### Defining Components

```clojure
;; Entity component (ident based on data)
(defsc Person [this {:person/keys [id name]}]
  {:query [:person/id :person/name]
   :ident :person/id}  ; Keyword shorthand: [:person/id <id from props>]
  (dom/div name))

;; Singleton/Panel component (constant ident)
(defsc FriendsList [this {:keys [friends]}]
  {:query [{:friends (comp/get-query Person)}]
   :ident (fn [] [:component/id :friends-list])}  ; Constant!
  (dom/div
    (map ui-person friends)))

;; Root component
(defsc Root [this {:keys [main-panel]}]
  {:query [{:main-panel (comp/get-query MainPanel)}]
   :ident (fn [] [:component/id :root])
   :initial-state {:main-panel {}}}
  (ui-main-panel main-panel))
```

### Component Ident Patterns

```clojure
;; 1. Keyword shorthand (most common for entity data)
:ident :person/id
;; Becomes: [:person/id <value-of-:person/id-in-props>]

;; 2. Constant ident (UI component/singleton)
:ident (fn [] [:component/id :my-panel])
;; Always: [:component/id :my-panel]

;; 3. Computed ident (custom logic)
:ident (fn [this props]
         [:person/by-email (:person/email props)])
```

## Loading Data into Nested UI

### Example: Load into Specific Subtree

```clojure
(defsc MainPanel [this {:keys [current-user friends-list]}]
  {:query [{:current-user (comp/get-query User)}
           {:friends-list (comp/get-query FriendsList)}]
   :ident (fn [] [:component/id :main-panel])
   :initial-state {:current-user {} :friends-list {}}}

  (dom/div
    (ui-user current-user)
    (ui-friends-list friends-list)))

(defsc FriendsList [this {:keys [friends load-friends?]}]
  {:query [:load-friends? {:friends (comp/get-query Person)}]
   :ident (fn [] [:component/id :friends-list])}

  (dom/div
    (dom/h3 "Friends")
    (if load-friends?
      (dom/div "Loading...")
      (if (seq friends)
        (map ui-person friends)
        (dom/button
          {:onClick #(comp/transact! this
                       [(load-friends-list {})])}
          "Load Friends")))))
```

### The Complete Load Flow

```
┌─────────────────────────────────────────────────────────────┐
│ 1. Component triggers load                                   │
├─────────────────────────────────────────────────────────────┤
│ (df/load! this :all-friends Person                           │
│   {:target [:component/id :friends-list :friends]})          │
└─────────────────────────────────────────────────────────────┘
                           ↓
┌─────────────────────────────────────────────────────────────┐
│ 2. EQL Query sent to server                                  │
├─────────────────────────────────────────────────────────────┤
│ [{:all-friends [:person/id :person/name]}]                   │
│                                                               │
│ Note: Query includes component metadata for normalization    │
└─────────────────────────────────────────────────────────────┘
                           ↓
┌─────────────────────────────────────────────────────────────┐
│ 3. Server response (tree structure)                          │
├─────────────────────────────────────────────────────────────┤
│ {:all-friends [{:person/id 2 :person/name "Bob"}             │
│                {:person/id 3 :person/name "Eve"}             │
│                {:person/id 4 :person/name "Carol"}]}         │
└─────────────────────────────────────────────────────────────┘
                           ↓
┌─────────────────────────────────────────────────────────────┐
│ 4. Normalization (using Person's ident)                      │
├─────────────────────────────────────────────────────────────┤
│ Normalized data:                                             │
│ {:person/id {2 {:person/id 2 :person/name "Bob"}             │
│              3 {:person/id 3 :person/name "Eve"}             │
│              4 {:person/id 4 :person/name "Carol"}}          │
│  :all-friends [[:person/id 2]                                │
│                [:person/id 3]                                │
│                [:person/id 4]]}                              │
└─────────────────────────────────────────────────────────────┘
                           ↓
┌─────────────────────────────────────────────────────────────┐
│ 5. Targeting (place idents at target location)               │
├─────────────────────────────────────────────────────────────┤
│ Target: [:component/id :friends-list :friends]               │
│                                                               │
│ Result in app state:                                         │
│ {:component/id                                               │
│   {:friends-list                                             │
│     {:friends [[:person/id 2]      ; ← Idents placed here!   │
│                [:person/id 3]                                │
│                [:person/id 4]]}}}                            │
│                                                               │
│ Person tables already populated from step 4                  │
└─────────────────────────────────────────────────────────────┘
```

### Load Behavior Without :target

```clojure
;; ❌ COMMON MISTAKE: Expecting data to automatically appear in UI
(df/load! this :friends Person)
;; This normalizes: {:person/id {1 {...} 2 {...}}}
;; AND creates: {:friends [[:person/id 1] [:person/id 2]]} AT ROOT
;; But the :friends edge at root won't show in your UI unless your
;; Root component queries for it! The data is normalized but has
;; NO CONNECTION to any component that's currently rendering.

;; ✅ CORRECT: Explicit targeting to place in tree
(df/load! this :friends Person
  {:target [:component/id :friends-list :friends]})
;; This normalizes AND places idents at the target location
;; Now the FriendsList component will see the data!

;; Loading by ident - only normalizes, doesn't place in tree
(df/load! this [:person/id 42] Person)
;; Result: {:person/id {42 {...}}}
;; The ident [:person/id 42] is NOT placed anywhere in the tree!
;; If a component already has [:person/id 42] as a prop, it will
;; see the updated data. Otherwise, you need :target.

;; To place the ident in the tree, use :target
(df/load! this [:person/id 42] Person
  {:target [:component/id :main-panel :current-person]})
;; Now [:person/id 42] is placed at [:component/id :main-panel :current-person]
```

## Mutations and EQL Symbols

### Key Point: Mutations Use Symbols, Not Keywords!

```clojure
(defmutation create-person
  "Create a new person"
  [{:keys [name age]}]

  (action [{:keys [state]}]
    ;; Optimistic update
    (swap! state ...))

  (remote [env]
    ;; Return the created person
    (m/returning env Person)))
```

### EQL for Mutations

```
┌─────────────────────────────────────────────────────────────┐
│ Client Transaction                                           │
├─────────────────────────────────────────────────────────────┤
│ (comp/transact! this                                         │
│   [(create-person {:name "Alice" :age 30})])                 │
└─────────────────────────────────────────────────────────────┘
                           ↓
┌─────────────────────────────────────────────────────────────┐
│ EQL Sent to Server (SYMBOL, not keyword!)                    │
├─────────────────────────────────────────────────────────────┤
│ [(create-person {:name "Alice" :age 30})]                    │
│   ^                                                           │
│   └─ Symbol (no namespace colon)                             │
│                                                               │
│ With return value (mutation join):                           │
│ [{(create-person {:name "Alice" :age 30})                    │
│   [:person/id :person/name :person/age]}]                    │
└─────────────────────────────────────────────────────────────┘
                           ↓
┌─────────────────────────────────────────────────────────────┐
│ Server Response (keyed by SYMBOL for mutation joins)         │
├─────────────────────────────────────────────────────────────┤
│ {create-person {:person/id 42                                │
│                 :person/name "Alice"                          │
│                 :person/age 30}}                              │
│  ^                                                            │
│  └─ SYMBOL as key (only when using mutation join/returning)  │
└─────────────────────────────────────────────────────────────┘
                           ↓
┌─────────────────────────────────────────────────────────────┐
│ Normalized and Merged                                        │
├─────────────────────────────────────────────────────────────┤
│ {:person/id {42 {:person/id 42                               │
│                  :person/name "Alice"                         │
│                  :person/age 30}}}                            │
└─────────────────────────────────────────────────────────────┘
```

### Mutation with Targeting

```clojure
(defmutation create-person [person-data]
  (action [{:keys [state]}]
    ;; Optimistic: create temp ID
    (let [temp-id (tempid/tempid)
          person (assoc person-data :person/id temp-id)]
      (merge/merge-component! state Person person
        :append [:component/id :friends-list :friends])))

  (remote [env]
    (-> env
      (m/returning Person)
      ;; Server's person will be placed here
      (m/with-target (targeting/append-to
                       [:component/id :friends-list :friends])))))

;; EQL sent (mutation join):
[{(create-person {:name "Alice"})
  [:person/id :person/name]}]

;; Response (SYMBOL key):
{create-person {:person/id 42 :person/name "Alice"}}

;; Result: Person normalized, ident appended to target
```

## Query Composition: The Foundation

### Why Composition Matters

```clojure
;; ❌ WRONG: Manual query construction loses metadata
(defsc FriendsList [this props]
  {:query [{:friends [:person/id :person/name]}]}  ; NO!
  ...)

;; ✅ RIGHT: Composition preserves component metadata
(defsc FriendsList [this props]
  {:query [{:friends (comp/get-query Person)}]}  ; YES!
  ...)
```

The metadata includes:
- Component class (for calling ident function)
- Query transform information
- Dynamic query state

### Nested Query Composition

```clojure
(defsc Avatar [this {:keys [avatar/url]}]
  {:query [:avatar/url]
   :ident :user/id}  ; Shares user's ident
  (dom/img {:src url}))

(defsc User [this {:user/keys [id name avatar]}]
  {:query [:user/id :user/name
           {:user/avatar (comp/get-query Avatar)}]  ; Compose!
   :ident :user/id}
  (dom/div
    (dom/h3 name)
    (ui-avatar avatar)))

(defsc FriendsList [this {:keys [friends]}]
  {:query [{:friends (comp/get-query User)}]  ; Compose!
   :ident (fn [] [:component/id :friends-list])}
  (dom/div
    (map ui-user friends)))

(defsc MainPanel [this {:keys [friends-list]}]
  {:query [{:friends-list (comp/get-query FriendsList)}]  ; Compose!
   :ident (fn [] [:component/id :main-panel])}
  (ui-friends-list friends-list))

;; Resulting query sent to server:
[{:main-panel
  [{:friends-list
    [{:friends
      [:user/id :user/name
       {:user/avatar [:avatar/url]}]}]}]}]
```

### When Lambda Query Form is Required

```clojure
;; Union queries MUST use lambda form
(defsc UnionComponent [this props]
  {:query (fn [] {:person (comp/get-query Person)
                  :place (comp/get-query Place)})
   :ident (fn [this props] ...)}
  ...)

;; Link queries MUST use lambda form
(defsc Component [this props]
  {:query (fn [] [:some/field [df/marker-table '_]])}
  ...)
```

## Targeting Deep Structures

### The Three-Element Rule

Because of normalization, targeting NEVER requires deep paths:

```clojure
;; UI Structure:
Root → MainPanel → UserProfile → FriendsList → Person

;; ❌ You DON'T need this:
{:target [:component/id :root
          :main-panel
          :user-profile
          :friends-list
          :friends]}

;; ✅ You only need this:
{:target [:component/id :friends-list :friends]}
```

### Targeting Examples

```clojure
;; 1. Load to ROOT (default behavior with keyword)
(df/load! app :all-people Person)
;; Result: {:all-people [[:person/id 1] [:person/id 2]]} at ROOT
;; Data is normalized in tables, but edge at root won't show
;; unless Root component queries for :all-people

;; 2. Load with explicit target into component field
(df/load! app :friends Person
  {:target [:component/id :friends-list :friends]})
;; Normalizes AND places idents at [:component/id :friends-list :friends]

;; 3. Load specific entity by ident (only normalizes)
(df/load! app [:user/id 42] User)
;; Result: {:user/id {42 {...}}} in tables
;; The ident [:user/id 42] is NOT placed anywhere in the tree!

;; 4. Load specific entity AND place in tree with :target
(df/load! app [:user/id 42] User
  {:target [:component/id :main-panel :current-user]})
;; Normalizes into table AND places [:user/id 42] at target location

;; 5. Append to existing list
(df/load! app :new-friends Person
  {:target (targeting/append-to [:component/id :friends-list :friends])})
;; Normalizes and appends idents to the list

;; 6. Multiple targets (same data, multiple locations)
(df/load! app [:user/id 42] User
  {:target [[:component/id :main-panel :current-user]
            (targeting/append-to [:component/id :user-list :users])]})
;; Normalizes once, places ident at multiple locations
```

## Normalization Details

### The `tree->db` Function

From `normalize.cljc`:

```clojure
(fnorm/tree->db query data-tree merge-tables?)

;; query: The query with component metadata
;; data-tree: Tree response from server
;; merge-tables?: If true, returns flat map; if false, returns with metadata
```

### How Normalization Works

```
1. Walk the query and data tree in parallel
2. For each join with component metadata:
   a. Call component's ident function on data
   b. Place entity at ident location in table
   c. Replace data with ident reference
3. Return normalized structure
```

### Example Step-by-Step

```clojure
;; Query with metadata
[{:friends ^{:component Person} [:person/id :person/name]}]

;; Data
{:friends [{:person/id 2 :person/name "Bob"}
           {:person/id 3 :person/name "Eve"}]}

;; Step 1: Process :friends (sees Person metadata)
;; Step 2: For each person, call Person's ident function
;;   (ident Person {:person/id 2 ...}) => [:person/id 2]
;;   (ident Person {:person/id 3 ...}) => [:person/id 3]

;; Step 3: Build normalized structure
{:person/id {2 {:person/id 2 :person/name "Bob"}
             3 {:person/id 3 :person/name "Eve"}}
 :friends [[:person/id 2] [:person/id 3]]}
```

## Merge Strategy

### Mark-Missing and Sweep

From `merge.cljc`:

```clojure
;; Current state
{:person/id {1 {:person/id 1
                :person/name "Alice"
                :person/age 30
                :person/phone "555-1234"}}}

;; Query (only asks for name)
[:person/id :person/name]

;; Response
{:person/id 1 :person/name "Alice Smith"}

;; After merge (age and phone PRESERVED!)
{:person/id {1 {:person/id 1
                :person/name "Alice Smith"    ; Updated
                :person/age 30                ; Preserved (not in query)
                :person/phone "555-1234"}}}   ; Preserved (not in query)
```

**Rules**:
1. Data in query AND response → Updated
2. Data in query but NOT in response → Removed
3. Data NOT in query → Preserved

### Pre-merge Hook

Transform data before normalization:

```clojure
(defsc Person [this props]
  {:query [:person/id :person/name]
   :ident :person/id
   :pre-merge (fn [{:keys [current-normalized data-tree state-map query]}]
                ;; current-normalized: Existing entity in DB (or nil)
                ;; data-tree: New data from server
                ;; state-map: Full app state
                ;; query: The query used
                (merge
                  {:person/ui-selected? false}  ; Defaults
                  current-normalized             ; Existing data
                  data-tree))}                   ; New data (wins)
  ...)
```

## Transaction Processing

### Sequential vs Parallel

```clojure
;; DEFAULT: Sequential processing
(comp/transact! this [(create-person {...})])
(comp/transact! this [(create-other {...})])
;; Second waits for first to complete

;; PARALLEL: Don't wait
(comp/transact! this [(create-person {...})]
  {:parallel? true})
(comp/transact! this [(create-other {...})]
  {:parallel? true})
;; Both execute simultaneously
```

### Write-Before-Read Optimization

From `tx_processing.cljc`:

```clojure
;; You write:
(comp/transact! this
  [(create-person {...})
   (df/load! :people Person)])

;; Fulcro automatically reorders to:
;; 1. create-person (write/mutation)
;; 2. load people (read/query)
;; Ensures the load sees the newly created person
```

## Common Patterns

### Master-Detail with Load on Select

```clojure
(defsc PersonRow [this {:person/keys [id name]}]
  {:query [:person/id :person/name]
   :ident :person/id}
  (dom/div
    {:onClick #(comp/transact! this
                 [(select-person {:person/id id})])}
    name))

(defmutation select-person [{:person/id id}]
  (action [{:keys [state app]}]
    ;; Set current selection
    (swap! state assoc-in [:component/id :main-panel :current-person]
      [:person/id id])
    ;; Load full details if needed
    (let [person (get-in @state [:person/id id])
          has-details? (contains? person :person/age)]
      (when-not has-details?
        (df/load! app [:person/id id] PersonDetail))))
  (remote [_] false))

(defsc PersonDetail [this {:person/keys [id name age addresses]}]
  {:query [:person/id :person/name :person/age
           {:person/addresses (comp/get-query Address)}]
   :ident :person/id}
  (dom/div
    (dom/h2 name)
    (dom/p "Age: " age)
    (when (seq addresses)
      (map ui-address addresses))))

(defsc MainPanel [this {:keys [people current-person]}]
  {:query [{:people (comp/get-query PersonRow)}
           {:current-person (comp/get-query PersonDetail)}]
   :ident (fn [] [:component/id :main-panel])
   :initial-state {:people [] :current-person {}}}
  (dom/div
    (dom/div "People:"
      (map ui-person-row people))
    (dom/div "Detail:"
      (when current-person
        (ui-person-detail current-person)))))
```

### Lazy Loading Children with Load Markers

```clojure
(defsc Person [this {:person/keys [id name addresses] :as props}]
  {:query (fn [] [:person/id :person/name
                  {:person/addresses (comp/get-query Address)}
                  [df/marker-table '_]])  ; ← Required for load markers!
   :ident :person/id}

  (let [marker (get props [df/marker-table :person-addresses])
        loading? (df/loading? marker)]
    (dom/div
      (dom/h3 name)
      (cond
        loading?
        (dom/div "Loading addresses...")

        (seq addresses)
        (map ui-address addresses)

        :else
        (dom/button
          {:onClick #(df/load-field! this :person/addresses
                       {:marker :person-addresses})}
          "Load Addresses")))))
```

**Key Points**:
- Link query `[df/marker-table '_]` is required for load markers and MUST use lambda query form
- Marker is accessed from props: `(get props [df/marker-table :person-addresses])`
- `df/loading?` checks the marker state
- `df/load-field!` is a helper equivalent to loading by the component's ident with a specific field
- No custom mutation needed - load markers handle state automatically

### Helper: `refresh!`

```clojure
;; refresh! is a shorthand for reloading a component's data
(df/refresh! this)

;; It's equivalent to:
(df/load! this (comp/get-ident this) ComponentClass)

;; Useful for refreshing entity data after server changes
```

## Key Namespaces

```clojure
[com.fulcrologic.fulcro.components :as comp]
[com.fulcrologic.fulcro.mutations :as m :refer [defmutation]]
[com.fulcrologic.fulcro.data-fetch :as df]
[com.fulcrologic.fulcro.algorithms.merge :as merge]
[com.fulcrologic.fulcro.algorithms.normalize :as fnorm]
[com.fulcrologic.fulcro.algorithms.data-targeting :as targeting]
[com.fulcrologic.fulcro.algorithms.tempid :as tempid]
[com.fulcrologic.fulcro.dom :as dom]
```

## Debugging Tips

### 1. Visualize Idents

```clojure
;; In a mutation or event handler
(let [ident (comp/get-ident this)]
  (js/console.log "My ident:" ident))

;; See what's at an ident
(get-in @state [:person/id 42])
```

### 2. Check Targets

```clojure
;; See what's at a target location
(get-in @state [:component/id :friends-list :friends])
;; Should be idents: [[:person/id 2] [:person/id 3]]
```

### 3. Inspect Queries

```clojure
;; See generated query
(comp/get-query Root @state)

;; See query metadata
(-> (comp/get-query Person) meta)
```

### 4. Trace Normalization

```clojure
;; Manual normalization to see result
(fnorm/tree->db
  [{:friends (comp/get-query Person)}]
  {:friends [{:person/id 2 :person/name "Bob"}]}
  true)
```

## Summary: The Fulcro Way

1. **Nested UI**: Each component has an ident (dynamic or constant)
2. **Query Composition**: Queries compose from leaf to root using `get-query`
3. **EQL Communication**: Mutations use symbols, queries use keywords
4. **Normalization**: Automatic via component idents and query metadata
5. **Simple Targeting**: Max 3 elements `[table id field]` reaches ANY depth
6. **Intelligent Merge**: Preserves data not in query, removes missing data
7. **Single Transaction Model**: `transact!` for everything

**The Power**: You load into a specific subtree of a deeply nested UI with a simple 3-element target, because normalization flattens everything. The UI nesting doesn't require deep paths in the database.

**The Gotcha**: Data normalization is automatic, but edge creation is NOT. Without `:target`, your data goes into tables but won't appear in UI unless a rendered component already queries for it.
