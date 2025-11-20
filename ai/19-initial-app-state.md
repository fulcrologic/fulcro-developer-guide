
# Initial Application State in Fulcro

## Overview

Initial application state in Fulcro is the process of establishing a starting normalized client-side database before the application begins. This state represents the first frame in a sequence of application states that will be rendered over time, similar to frames in a movie.

## Fundamentals and Tree Structure

### Core Concepts

- **Normalized Database**: Fulcro applications maintain state as a normalized graph database on the client
- **Co-location**: Initial state is co-located with components, similar to queries
- **Composition**: Initial state composes from child to parent components, mirroring the UI tree structure
- **Tree-to-Graph Transformation**: Fulcro automatically normalizes the tree structure into a graph database using queries and ident functions

### Why Co-location Matters

1. **Local Reasoning**: Each component defines its own initial state requirements
2. **Refactoring Safety**: Moving components means moving both query and state together
3. **Union Support**: Fulcro can automatically initialize alternate branches of union components

## Component Initial State Patterns

### Basic Pattern

Every component that needs initial state follows this pattern:

```clojure
(defsc ComponentName [this props]
  {:initial-state (fn [params] {...})  ; Function that returns initial state map
   :ident         :component/id  ; How to identify this component in normalized DB (template form)
   :query         [:component/id ...]} ; What data this component needs
  ...)
```

### Simple Component Example

```clojure
(defsc Child [this props]
  {:initial-state (fn [params] {:child/id 1})
   :ident         :child/id
   :query         [:child/id]}
  ...)
```

### Composed Components

```clojure
(defsc Parent [this props]
  {:initial-state (fn [params]
                    {:parent/id 1
                     :parent/child (comp/get-initial-state Child)})
   :ident         :parent/id
   :query         [:parent/id {:parent/child (comp/get-query Child)}]}
  ...)
```

### With Parameters

Components can accept parameters for customized initial state:

```clojure
(defsc ItemLabel [this {:keys [value]}]
  {:initial-state (fn [{:keys [value]}] {:value value})
   :query         [:value]
   :ident         (fn [] [:labels/by-value value])}
  (dom/p value))

;; Usage in parent component
(defsc Foo [this {:keys [label]}]
  {:initial-state (fn [{:keys [id label]}] 
                    {:id    id 
                     :type  :foo 
                     :label (comp/get-initial-state ItemLabel {:value label})})
   :query         [:type :id {:label (comp/get-query ItemLabel)}]}
  ...)
```

## Database Initialization and Bootstrapping

### Automatic Detection

Fulcro automatically detects initial state on the root component and uses it to bootstrap the application database. No manual setup is required.

### Normalization Process

1. Fulcro starts with the root component's initial state (a tree)
2. Walks through the tree structure following component relationships
3. Normalizes the tree into tables based on component idents
4. Creates a graph database with proper references

**Important**: Only components with `:ident` functions get normalized into tables. Components without idents remain as nested data in their parent's properties.

### Root Component Setup

```clojure
(defsc Root [this {:keys [panes items]}]
  {:initial-state (fn [params] 
                    {:panes (comp/get-initial-state PaneSwitcher nil)
                     :items (comp/get-initial-state ListItem nil)})
   :query         [{:items (comp/get-query ListItem)}
                   {:panes (comp/get-query PaneSwitcher)}]}
  ...)
```

## Union Handling and Alternate Branches

### The Union Challenge

Union queries represent "this could be one of several component types." For to-one relations, only one branch can exist in the initial state tree, but you often want all possible components available in the database.

### Example Union Structure

```clojure
;; Union query means "could be person OR place"
{:person (comp/get-query Person) :place (comp/get-query Place)}
```

### To-One Union Pattern

```clojure
(defsc PersonPlaceUnion [this props]
  {:initial-state (fn [p] 
                    ;; Can only initialize one branch in the tree
                    (comp/get-initial-state Person {:id 1 :name "Joe"}))
   :query (fn [] {:person (comp/get-query Person) 
                  :place  (comp/get-query Place)})
   :ident (fn [] [(:type props) (:id props)])}
  ...)
```

### Automatic Branch Initialization

Fulcro solves the "missing branches" problem automatically:

1. At startup, walks the root query
2. For each union component found, checks all branches for initial state
3. Places initial state for each branch in the appropriate table
4. Branches not explicitly included in tree are still available for routing

### To-Many Union Pattern

```clojure
(defsc ListItem [this {:keys [id type] :as props}]
  {:initial-state (fn [params] 
                    ;; Returns vector of different component types
                    [(comp/get-initial-state Bar {:id 1 :label "A"}) 
                     (comp/get-initial-state Foo {:id 2 :label "B"}) 
                     (comp/get-initial-state Bar {:id 3 :label "C"})])
   :query         (fn [] {:foo (comp/get-query Foo) :bar (comp/get-query Bar)})
   :ident         (fn [] [type id])}
  ...)
```

## Common Patterns and Best Practices

### 1. Parameter Passing

Always design initial state functions to accept parameters for flexibility:

```clojure
;; Good - accepts parameters
{:initial-state (fn [{:keys [id name type]}] 
                  {:id id :name name :type type})}

;; Less flexible - hardcoded values
{:initial-state (fn [params] 
                  {:id 1 :name "Default" :type :user})}
```

### 2. Symmetric Structure

Keep initial state and query structure in sync:

```clojure
;; Query structure
[:parent/id {:parent/child (comp/get-query Child)}]

;; Matching initial state structure
{:parent/id 1 :parent/child (comp/get-initial-state Child)}
```

### 3. Union Component Design

Union components should be pure routing components with no state or rendering of their own:

```clojure
(defsc RouterComponent [this {:keys [id type] :as props}]
  {:initial-state (fn [params] (comp/get-initial-state DefaultChild nil))
   :query         (fn [] {:child-a (comp/get-query ChildA) 
                          :child-b (comp/get-query ChildB)})
   :ident         (fn [] [type id])}
  (case type
    :child-a (ui-child-a props)
    :child-b (ui-child-b props)
    (dom/p "Unknown route")))
```

### 4. Ident Patterns

Use consistent ident patterns:

```clojure
;; For components with ID property - template form (most common)
:ident :component/id
;; Expands to: (fn [] [:component/id (:component/id props)])

;; For components with computed idents - lambda form
:ident (fn [] [:component/by-id (:component/id props)])

;; For singleton components
:ident (fn [] [:component :singleton])

;; For union components (determined at runtime from props)
:ident (fn [] [(:type props) (:id props)])
```

## Practical Examples

### Complete Example: Multi-Level Application

```clojure
(ns app.initial-state-example
  (:require [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
            [com.fulcrologic.fulcro.dom :as dom]))

;; Leaf component
(defsc User [this {:keys [user/id user/name user/email]}]
  {:initial-state (fn [{:keys [id name email]}]
                    {:user/id    id
                     :user/name  name
                     :user/email email})
   :ident         :user/id
   :query         [:user/id :user/name :user/email]}
  (dom/div
    (dom/h3 name)
    (dom/p email)))

(def ui-user (comp/factory User {:keyfn :user/id}))

;; Collection component
(defsc UserList [this {:keys [user-list/id user-list/users]}]
  {:initial-state (fn [params]
                    {:user-list/id    :main
                     :user-list/users [(comp/get-initial-state User {:id 1 :name "Alice" :email "alice@example.com"})
                                       (comp/get-initial-state User {:id 2 :name "Bob" :email "bob@example.com"})]})
   :ident         :user-list/id
   :query         [:user-list/id {:user-list/users (comp/get-query User)}]}
  (dom/div
    (dom/h2 "Users")
    (dom/ul
      (map ui-user users))))

(def ui-user-list (comp/factory UserList))

;; Settings page (union branch)
(defsc SettingsPage [this {:keys [settings/title]}]
  {:initial-state (fn [params] 
                    {:page/type     :settings
                     :page/id       :singleton
                     :settings/title "Application Settings"})
   :query         [:page/type :page/id :settings/title]}
  (dom/div
    (dom/h1 title)
    (dom/p "Settings content here...")))

(def ui-settings (comp/factory SettingsPage))

;; Main page (union branch)  
(defsc MainPage [this {:keys [main/user-list]}]
  {:initial-state (fn [params]
                    {:page/type      :main
                     :page/id        :singleton  
                     :main/user-list (comp/get-initial-state UserList)})
   :query         [:page/type :page/id {:main/user-list (comp/get-query UserList)}]}
  (dom/div
    (dom/h1 "Main Page")
    (ui-user-list user-list)))

(def ui-main (comp/factory MainPage))

;; Page router (union component)
(defsc PageRouter [this {:keys [page/type] :as props}]
  {:initial-state (fn [params] (comp/get-initial-state MainPage))
   :query         (fn [] {:main     (comp/get-query MainPage)
                          :settings (comp/get-query SettingsPage)})
   :ident         (fn [] [type (:page/id props)])}
  (case type
    :main     (ui-main props)
    :settings (ui-settings props)
    (dom/p "Unknown page")))

(def ui-page-router (comp/factory PageRouter))

;; Root component
(defsc Root [this {:keys [current-page]}]
  {:initial-state (fn [params] 
                    {:current-page (comp/get-initial-state PageRouter)})
   :query         [{:current-page (comp/get-query PageRouter)}]}
  (dom/div
    (dom/nav
      (dom/button {:onClick #(comp/transact! this '[(navigate {:page :main})])} "Main")
      (dom/button {:onClick #(comp/transact! this '[(navigate {:page :settings})])} "Settings"))
    (ui-page-router current-page)))
```

### Resulting Normalized Database

The above example creates this normalized structure:

```clojure
{:user/id           {1 {:user/id 1 :user/name "Alice" :user/email "alice@example.com"}
                     2 {:user/id 2 :user/name "Bob" :user/email "bob@example.com"}}
 :user-list/id      {:main {:user-list/id :main 
                            :user-list/users [[:user/id 1] [:user/id 2]]}}
 [:main :singleton] {:page/type :main :page/id :singleton 
                     :main/user-list [:user-list/id :main]}
 [:settings :singleton] {:page/type :settings :page/id :singleton
                         :settings/title "Application Settings"}
 :current-page      [:main :singleton]}
```

<!-- TODO: Verify this claim - the actual table keys for union branches might be different -->

## State Progressions and Testing

### Conceptual Model

Think of your application as a sequence of states rendered over time:

```
Initial State → [Mutation] → State 2 → [Mutation] → State 3 → ...
```

### Testing Benefits

Since rendering is a pure function of state, you can:

1. **Unit Test Mutations**: Test state transformations without UI
2. **Initialize to Any State**: Run mutations on initial state to reach any application state
3. **Record and Replay**: Capture mutation sequences for debugging/demos
4. **Teleport Development**: Jump to specific application states during development

<!-- TODO: Verify this claim - need to check actual testing patterns for mutations -->

## Key Takeaways

1. **Co-locate state with components** for maintainability and refactoring safety
2. **Use the same composition pattern** for both queries and initial state  
3. **Design for parameters** to make components flexible and reusable
4. **Let Fulcro handle normalization** - focus on the tree structure, provide idents for components that need normalization
5. **Union components are routers** - they coordinate but don't own state
6. **Think in state progressions** for testing and debugging
7. **Initial state enables powerful development workflows** like state teleportation and mutation replay

The initial state system in Fulcro provides a foundation for predictable, testable applications while maintaining the co-location benefits that make large applications manageable.
