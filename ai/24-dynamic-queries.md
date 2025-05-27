# Dynamic Queries in Fulcro

## Overview

Dynamic queries allow you to change a component's query at runtime. This feature is fully serializable (works with time-travel debugging) and essential for code splitting, where parent components can't compose child queries until they're loaded.

## Query IDs

### Purpose and Structure

Dynamic queries are stored in the application database and must be serializable. Each dynamic query is identified by a **Query ID** consisting of:

- Component's fully-qualified class name
- User-defined qualifier (defaults to empty string)

### Creating Query IDs

```clojure
(defsc Thing [this props]
  {:query [:id :name]
   :ident [:thing/id :id]}
  ...)

;; Different query instances of the same component
(def ui-thing (comp/factory Thing))                    ; No qualifier
(def ui-thing-admin (comp/factory Thing {:qualifier :admin})) ; :admin qualifier
(def ui-thing-public (comp/factory Thing {:qualifier :public})) ; :public qualifier
```

### Using Qualified Factories

```clojure
(defsc Parent [this props]
  {:query (fn [] [{:admin-thing (comp/get-query ui-thing-admin)}
                  {:public-thing (comp/get-query ui-thing-public)}])}
  (dom/div
    (ui-thing-admin (:admin-thing props))
    (ui-thing-public (:public-thing props))))
```

Now you can set different queries for "Thing with qualifier :admin" vs "Thing with qualifier :public".

## Setting Dynamic Queries

### Basic Query Change

```clojure
(defmutation change-query [_]
  (action [{:keys [state]}]
    (swap! state comp/set-query* MyComponent {:query [:id :new-field]})))
```

### Critical Normalization Requirement

When changing queries with joins, you **must** use `get-query` to preserve normalization metadata:

```clojure
;; WRONG - Missing normalization metadata
(defmutation bad-query-change [_]
  (action [{:keys [state]}]
    (swap! state comp/set-query* Parent 
           {:query [:id {:child [:x :y]}]}))) ; [:x :y] lacks metadata

;; CORRECT - Using get-query for proper metadata
(defmutation good-query-change [_]
  (action [{:keys [state]}]
    (swap! state comp/set-query* Parent 
           {:query [:id {:child (comp/get-query Child)}]})))
```

### Complex Multi-Component Query Changes

```clojure
(defsc Child [this props]
  {:query [:child/id :child/name]
   :ident [:child/id :child/id]}
  ...)

(defsc Parent [this props]
  {:query [:parent/id {:parent/child (comp/get-query Child)}]
   :ident [:parent/id :parent/id]}
  ...)

;; Change both parent and child queries
(defmutation update-both-queries [_]
  (action [{:keys [state]}]
    (swap! state 
           (fn [s]
             (as-> s state-map
               ;; First update child query
               (comp/set-query* state-map Child 
                               {:query [:child/id :child/name :child/email]})
               ;; Then update parent query using updated child query
               (comp/set-query* state-map Parent 
                               {:query [:parent/id :parent/title 
                                       {:parent/child (comp/get-query Child state-map)}]}))))))
```

### Top-Level API

```clojure
;; Outside of mutations
(comp/set-query! app MyComponent {:query [:id :new-field]})
```

## Real-World Example

### Code Splitting Scenario

```clojure
(defsc LazyComponent [this props]
  {:query [:id :data]}
  (dom/div "Lazy loaded content"))

(defsc MainComponent [this props]
  {:query (fn [] (cond-> [:main/id]
                   (loaded? LazyComponent) 
                   (conj {:lazy-content (comp/get-query LazyComponent)})))}
  (dom/div
    "Main content"
    (when (:lazy-content props)
      (ui-lazy-component (:lazy-content props)))))

;; When lazy component loads
(defmutation load-lazy-component [_]
  (action [{:keys [state]}]
    ;; Update query to include the lazy component
    (swap! state comp/set-query* MainComponent
           {:query [:main/id {:lazy-content (comp/get-query LazyComponent)}]})))
```

### Feature Toggle Implementation

```clojure
(defsc FeatureComponent [this props]
  {:query [:id :basic-data]
   :ident [:feature/id :id]}
  ...)

(defn enable-advanced-features! [app]
  (comp/set-query! app FeatureComponent
                   {:query [:id :basic-data :advanced-data :premium-features]}))

(defn disable-advanced-features! [app]
  (comp/set-query! app FeatureComponent
                   {:query [:id :basic-data]}))
```

## Hot Code Reload Challenges

### The Problem

Dynamic queries are stored in app state, so hot code reload doesn't automatically see static query changes in your code. This forces developers to either:
1. Manually reset queries with `set-query!` calls (tedious)
2. Reload the entire page (poor dev experience)

### How Dynamic Queries Work Internally

1. Query children are normalized into app state
2. Joins point to normalized child query values
3. Fulcro closes over the entire query tree (even for local component changes)

### The Solution: Query Refresh

Fulcro 3.3.0+ provides `comp/refresh-dynamic-queries!` to refresh static queries during hot reload:

```clojure
;; In your hot reload function
(defn ^:dev/after-load refresh []
  (comp/refresh-dynamic-queries! app)  ; Refresh static queries
  (app/mount! app RootComponent "app")) ; Re-mount app
```

### Preserving Dynamic Queries

Use `:preserve-dynamic-query? true` to prevent refresh of intentionally dynamic queries:

```clojure
(defsc DynamicComponent [this props]
  {:preserve-dynamic-query? true  ; Don't refresh this during hot reload
   :query (fn [] (if (:advanced-mode? @app-state)
                   [:id :name :advanced-features]
                   [:id :name]))}
  ...)
```

### Typical Hot Reload Setup

```clojure
(ns my-app.main
  (:require [com.fulcrologic.fulcro.application :as app]
            [com.fulcrologic.fulcro.components :as comp]))

(defonce app (app/fulcro-app {...}))

(defn ^:dev/after-load refresh []
  ;; Refresh queries that have changed in source code
  (comp/refresh-dynamic-queries! app)
  ;; Re-mount to pick up component changes
  (app/mount! app RootComponent "app"))

(defn init []
  (app/mount! app RootComponent "app"))

;; Hot reload will call refresh, initial load calls init
```

## Advanced Patterns

### Conditional Query Building

```clojure
(defsc AdaptiveComponent [this props]
  {:query (fn [] 
            (let [user-role (get-in @app-state [:current-user :role])]
              (cond-> [:id :name]
                (= user-role :admin) (conj :admin-data)
                (= user-role :premium) (conj :premium-features)
                :always (conj {:items (comp/get-query Item)}))))
   :preserve-dynamic-query? true}  ; Don't auto-refresh
  ...)

;; Update query when user role changes
(defmutation user-role-changed [{:keys [new-role]}]
  (action [{:keys [state]}]
    (swap! state assoc-in [:current-user :role] new-role)
    ;; Trigger query rebuild
    (comp/set-query! *app* AdaptiveComponent
                     (comp/get-query AdaptiveComponent @state))))
```

### Query Versioning

```clojure
(defsc VersionedComponent [this props]
  {:query [:id :name]  ; v1 query
   :ident [:versioned/id :id]}
  ...)

(def query-v2 [:id :name :email :phone])
(def query-v3 [:id :name :email :phone :address :preferences])

(defmutation upgrade-to-v2 [_]
  (action [{:keys [state]}]
    (swap! state comp/set-query* VersionedComponent {:query query-v2})))

(defmutation upgrade-to-v3 [_]
  (action [{:keys [state]}]
    (swap! state comp/set-query* VersionedComponent {:query query-v3})))
```

## Best Practices

1. **Always use `get-query`** for joins in dynamic query changes
2. **Use qualified factories** when you need multiple query variants of the same component  
3. **Mark intentionally dynamic components** with `:preserve-dynamic-query? true`
4. **Refresh queries during hot reload** for better dev experience
5. **Test normalization** after dynamic query changes
6. **Consider query versioning** for gradual feature rollouts
7. **Document query changes** in mutations for team understanding

## Common Pitfalls

### Forgetting Normalization Metadata

```clojure
;; BAD - Will break normalization
(comp/set-query* state MyComponent {:query [:id {:items [:item/id :item/name]}]})

;; GOOD - Preserves normalization
(comp/set-query* state MyComponent {:query [:id {:items (comp/get-query Item)}]})
```

### Not Preserving Intentional Dynamic Queries

```clojure
;; Without preserve flag, hot reload will reset your dynamic query
(defsc Router [this props]
  {:preserve-dynamic-query? true  ; Essential for routers!
   :query (fn [] (router-query this))}
  ...)
```

### Forgetting to Update Parent Queries

```clojure
;; When changing child query, parent query may also need updating
(defmutation add-child-field [_]
  (action [{:keys [state]}]
    (swap! state 
           (fn [s]
             (-> s
                 (comp/set-query* Child {:query [:id :name :new-field]})
                 (comp/set-query* Parent {:query [:id {:child (comp/get-query Child s)}]}))))))
```

Dynamic queries provide powerful runtime flexibility while maintaining Fulcro's declarative query model, enabling sophisticated applications with code splitting, progressive enhancement, and adaptive UIs.