
# Dynamic Routing

## Overview

Fulcro's Dynamic Router provides a powerful, composable routing system that integrates seamlessly with the component graph and query system. Routes are defined as component metadata, making them easy to refactor and compose.

## Core Concepts

### Router Benefits
- **Component-based**: Routes are declared on components themselves
- **Composable**: Nested routers work together automatically based on UI composition
- **Query integration**: Routes participate in the Fulcro query system
- **Flexible targeting**: Control where routed data is placed in the database
- **Type-safe parameters**: Route parameters can be validated and coerced at the component level

### Router Architecture
- **Dynamic Router**: Modern routing system based on component metadata (recommended)
- **Legacy UI Routers**: Older macro-based system (still supported, but not recommended for new apps)
- **Migration**: Both can coexist; migrate incrementally

## Basic Router Setup

### Route Target Components

Route targets are normal `defsc` components with routing metadata added:

```clojure
(ns app
  (:require
    [com.fulcrologic.fulcro.routing.dynamic-routing :as dr]))

;; Simple singleton route (no parameters needed)
(defsc HomePage [this props]
  {:query         [:home/title]
   :ident         (fn [] [:component/id ::HomePage])
   :route-segment ["home"]
   :will-enter    (fn [app params] (dr/route-immediate [:component/id ::HomePage]))}
  (dom/div "Home Page"))

;; Route with parameters and data loading
(defsc UserPage [this {:keys [user/id user/name]}]
  {:query         [:user/id :user/name]
   :ident         :user/id
   :route-segment ["user" :user-id]
   :will-enter    (fn [app {:keys [user-id]}]
                    (let [uid (if (string? user-id) (js/parseInt user-id) user-id)]
                      (dr/route-deferred [:user/id uid]
                        #(df/load! app [:user/id uid] UserPage
                           {:post-mutation        `dr/target-ready
                            :post-mutation-params {:target [:user/id uid]}}))))}
  (dom/div "User: " name))
```

### Router Component

The `defrouter` macro creates a router component that manages multiple route targets:

```clojure
(defrouter MainRouter [this {:keys [current-state route-factory route-props]}]
  {:router-targets [HomePage UserPage SettingsPage]}
  ;; Optional body: render UI for loading/error states
  (case current-state
    :initial (dom/div "Loading...")
    :pending (dom/div "Loading...")
    :failed (dom/div "Failed to load")
    ;; If no body is provided, the active route is rendered automatically
    ))
```

The `defrouter` macro automatically:
- Creates a query that includes queries from all targets
- Sets up ident based on the current route
- Manages routing state via an internal state machine
- Renders the appropriate route target or loading/error UI

```clojure
;; Root composition
(defsc Root [this {:root/keys [router]}]
  {:query         [{:root/router (comp/get-query MainRouter)}]
   :initial-state {:root/router {}}}
  (dom/div
    (ui-main-router router)))
```

## Route Segments

### Static Segments

Static route segments are literal strings that must match exactly:

```clojure
:route-segment ["home"]        ; matches /home
:route-segment ["admin" "users"] ; matches /admin/users
```

### Dynamic Segments

Dynamic segments capture path parameters as keywords:

```clojure
:route-segment ["user" :user-id]           ; matches /user/123
:route-segment ["post" :post-id "edit"]    ; matches /post/456/edit
:route-segment ["user" :user-id "post" :post-id] ; matches /user/123/post/456
```

Route parameters are **always strings** when captured from URLs. You must coerce them to the correct type (integer, UUID, etc.).

### Parameter Type Coercion

Use `:route-segment-params` to declare expected parameter types:

```clojure
;; String parameters (default, no conversion needed)
:route-segment [["search" :query]
:route-segment-params {:query :string}

;; Integer parameters
:route-segment ["page" :page-num]
:route-segment-params {:page-num :int}

;; UUID parameters
:route-segment ["item" :item-id]
:route-segment-params {:item-id :uuid}
```

Note: Parameter coercion is declarative but still requires handling in `:will-enter` for safe type conversion.

## Will-Enter Hook

The `:will-enter` hook is called when a component becomes the target of a route. It **must return** either `(route-immediate ident)` or `(dr/route-deferred ident f)`.

<!-- TODO: Verify exact signature of will-enter in current Fulcro version -->

### Immediate Routes

Use for components that don't require loading data:

```clojure
:will-enter (fn [app params]
              (dr/route-immediate [:component/id ::HomePage]))
```

The returned ident must point to existing data in the app state, or be a singleton that doesn't depend on parameters.

### Deferred Routes

Use for routes that require server data:

```clojure
:will-enter (fn [app {:keys [user-id]}]
              (dr/route-deferred [:user/id (js/parseInt user-id)]
                #(df/load! app [:user/id (js/parseInt user-id)] UserPage
                   {:post-mutation        `dr/target-ready
                    :post-mutation-params {:target [:user/id (js/parseInt user-id)]}})))
```

The function passed to `route-deferred` is a thunk (zero-argument function) that will be called to initiate the loading process. The route becomes active when `dr/target-ready` is called via the post-mutation.

### Conditional Routing

Perform authentication checks or other logic:

```clojure
:will-enter (fn [app params]
              (if (logged-in? app)
                (dr/route-immediate [:component/id ::DashboardPage])
                (dr/route-immediate [:component/id ::LoginPage])))
```

### Route Cancellation

Handle cleanup if a route is cancelled before completion:

```clojure
:route-cancelled (fn [{:keys [user-id]}]
                   ;; Cancel any pending operations
                   (abort-load-for-user user-id))
```

## Route Lifecycle

### Route Transitions

When you call `(dr/change-route! app-or-this path)`, the following happens:

1. **URL matching**: The router system matches the path vector against route segments from root to leaf
2. **Will-enter execution**: Each matching component's `:will-enter` is called with route parameters
3. **Route resolution**: 
   - `route-immediate` makes the route active immediately
   - `route-deferred` waits for the thunk to complete and `target-ready` to be called
4. **Rendering**: The active route's component is rendered

### Route Hooks

Components can declare several lifecycle hooks:

```clojure
:will-enter      ; Called when this route is being entered
                 ; Must return route-immediate or route-deferred

:will-leave      ; Called when this route is being left
                 ; Return false to prevent navigation (deprecated in 3.1.23+)

:allow-route-change? ; (v3.1.23+) Pure function to check if route change is allowed
                      ; Should not have side effects

:route-denied    ; (v3.1.23+) Called when allow-route-change? returns false
                 ; Can side-effect and use dr/retry-route! to override

:route-cancelled ; Called if a deferred route is cancelled
```

## Navigation

### Programmatic Navigation

Change routes from event handlers or mutations:

```clojure
;; Basic route change
(dr/change-route! this ["user" user-id])

;; With timeout customization
(dr/change-route! this ["user" user-id]
  {:error-timeout 2000 :deferred-timeout 100})

;; Replace history entry instead of pushing
(dr/change-route! this ["user" user-id]
  {:replace? true})
```

The `:error-timeout` (default 5000ms) is how long a deferred route waits before showing an error state. The `:deferred-timeout` (default 100ms) is how long before showing a pending state.

### Getting Current Route

Determine the current route programmatically:

```clojure
;; Get absolute path from root
(dr/current-route app Root)
; => ["user" "123" "profile"]

;; Get relative path from a component
(dr/current-route app UserComponent)
; => ["profile"]
```

### Route Links

Create links to routes without hardcoding paths:

```clojure
;; Using path-to for IDE navigation
(dom/a {:href (dr/path-to UserPage {:user-id user-id})}
  "View User")

;; Simpler form when parameters are in order
(dom/a {:href (dr/path-to UserPage user-id)}
  "View User")

;; Manual path building
(dom/a {:href (str "/" (clojure.string/join "/" ["user" user-id]))}
  "View User")
```

<!-- TODO: Verify if route-link exists and its API -->

## Nested Routing

### Nested Router Setup

Routers can be composed inside route targets, creating nested routing:

```clojure
(defsc UserSettings [this {:user/keys [id email settings-router]}]
  {:query         [:user/id :user/email {:user/settings-router (comp/get-query UserSettingsRouter)}]
   :ident         :user/id
   :route-segment ["settings"]
   :will-enter    (fn [app params]
                    (dr/route-immediate [:user/id (:user-id (dr/current-route app this))]))}
  (dom/div
    (dom/h2 "Settings for " id)
    (ui-settings-router settings-router)))

(defrouter UserSettingsRouter [this {:keys [current-state]}]
  {:router-targets [GeneralSettings PrivacySettings]}
  (case current-state
    :pending (dom/div "Loading settings...")
    :failed (dom/div "Failed to load settings")))

(defsc GeneralSettings [this props]
  {:query         [:setting/id :setting/name :setting/value]
   :ident         (fn [] [:settings/general])
   :route-segment ["general"]
   :will-enter    (fn [app params]
                    (dr/route-immediate [:settings/general]))}
  (dom/div "General Settings"))

(defsc PrivacySettings [this props]
  {:query         [:setting/id :setting/name :setting/value]
   :ident         (fn [] [:settings/privacy])
   :route-segment ["privacy"]
   :will-enter    (fn [app params]
                    (dr/route-immediate [:settings/privacy]))}
  (dom/div "Privacy Settings"))
```

### Relative Navigation

Navigate relative to a particular router, enabling better composition:

```clojure
;; Navigate to a sibling within a nested router
(dr/change-route-relative! this UserSettingsRouter (dr/path-to PrivacySettings))

;; Navigate up levels and then down
(dr/change-route-relative! this UserSettingsRouter (dr/path-to [".."] DashboardPage))
```

### Router State Composition

When a parent component is loaded dynamically, ensure the router's state is properly composed:

```clojure
(defsc Parent [this {:parent/keys [router]}]
  {:query         [{:parent/router (comp/get-query ChildRouter)}]
   :initial-state {:parent/router {}} ; Include router's initial state
   :pre-merge     (fn [{:keys [data-tree state-map]}]
                    (merge (comp/get-initial-state Parent)
                           {:parent/router (get-in state-map (comp/get-ident ChildRouter {}))}
                           data-tree))}
  (ui-child-router router))
```

## Router Rendering

### Default Behavior

If `defrouter` has no body, it automatically renders the active route:

```clojure
(defrouter SimpleRouter [this props]
  {:router-targets [Page1 Page2]})
```

### Custom Rendering with Body

Provide a body to render custom UI during loading/error states:

```clojure
(defrouter MyRouter [this {:keys [current-state route-factory route-props pending-path-segment]}]
  {:router-targets [Page1 Page2]}
  (case current-state
    :initial (dom/div "Not yet routed")
    :pending (dom/div
               (dom/div :.loader "Loading " pending-path-segment "...")
               (when route-factory
                 (route-factory route-props)))
    :failed (dom/div "Failed to load " pending-path-segment)
    ;; When routed, render the active target
    (route-factory route-props)))
```

Props provided to the router body:
- `:current-state` - `:initial`, `:pending`, `:routed`, or `:failed`
- `:pending-path-segment` - The path being loaded in pending/failed states (e.g. `["user" "123"]`)
- `:route-factory` - Function to render the active route target
- `:route-props` - Props for the current/old route

## Error Handling

### Route Not Found

Use a catch-all route segment (`["**"]`) for 404 handling:

```clojure
(defsc NotFoundPage [this props]
  {:query         [:not-found/id]
   :ident         (fn [] [:component/id ::NotFoundPage])
   :route-segment ["**"] ; catch-all
   :will-enter    (fn [app params] [:component/id ::NotFoundPage])}
  (dom/div "Page not found"))
```

Place this as the last target in the router so other routes match first.

### Failed Data Loading

Handle errors in deferred routes:

```clojure
:will-enter (fn [app {:keys [user-id]}]
              (dr/route-deferred [:user/id user-id]
                #(df/load! app [:user/id user-id] UserPage
                   {:error-action (fn [env]
                                    (dr/change-route! app ["not-found"]))})))
```

## Advanced Features

### Route Guards and Authentication

Prevent access to protected routes:

```clojure
(defsc ProtectedPage [this props]
  {:query         [:protected/data]
   :ident         (fn [] [:component/id ::ProtectedPage])
   :route-segment ["admin"]
   :will-enter    (fn [app params]
                    (if (authenticated? app)
                      (dr/route-immediate [:component/id ::ProtectedPage])
                      (do
                        (dr/change-route! app ["login"])
                        (dr/route-immediate [:component/id ::LoginPage]))))}
  (dom/div "Admin Content"))
```

### Query Parameters

Add extra parameters to routes beyond the `:route-segment`:

```clojure
;; Route change with extra parameters
(dr/change-route! this ["search"] {:q "clojure" :page 2})

;; Access in will-enter (v3.1.23+)
:will-enter (fn [app {:keys [q page] :as params}]
              ;; Route params include both segment params and extras
              (dr/route-deferred [:search/results]
                #(df/load! app [:search/results]
                   SearchResults
                   {:params {:query q :page page}})))
```

### Route Prevention with Validation

Prevent route changes based on component state (v3.1.23+):

```clojure
(defsc EditForm [this {:ui/keys [modified?]}]
  {:query [:ui/modified? :form/data]
   :ident (fn [] [:component/id ::EditForm])
   :route-segment ["edit"]
   :allow-route-change? (fn [this]
                          (not (:ui/modified? (comp/props this))))
   :route-denied (fn [this router relative-path]
                   (js/alert "Please save before navigating"))
   :will-enter (fn [app params]
                 (dr/route-immediate [:component/id ::EditForm]))}
  (dom/form ...))
```

### Code Splitting with Dynamic Routes

Lazy load route components based on route navigation:

```clojure
(defsc AdminPage [this props]
  {:route-segment ["admin"]
   :will-enter
   (fn [app params]
     (-> (js/import "./admin-pages.js")
         (.then
           (fn [module]
             (dr/route-immediate [:component/id ::AdminPage])))
         (.catch
           (fn [error]
             (log/error "Failed to load admin module" error)
             (dr/change-route! app ["home"])))))}
  (dom/div "Admin Interface"))
```

### IDE-Navigable Routing

Use `path-to` to create routes that are clickable in your IDE:

```clojure
;; Instead of this:
(dr/change-route! this ["root" "settings" "user-prefs"])

;; Use this:
(dr/change-route! this (dr/path-to Root Settings UserPreferences))

;; With parameters:
(dr/change-route! this (dr/path-to UserList UserDetail {:user-id user-id}))
```

This makes routes refactor-safe and IDE-navigable.

## Best Practices

### Route Design
- **Keep segments meaningful**: Use `/user/123` not `/u/123`
- **Make routes composable**: Each nested router should have its own routing targets
- **Include leaf targets**: Always route to the deepest component, not intermediate routers
- **Handle all states**: Provide UI for `:initial`, `:pending`, and `:failed` states

### Data Loading
- **Load in will-enter only**: Don't load data in component lifecycle methods
- **Use deferred routes**: For routes requiring server data, return `route-deferred`
- **Signal completion**: Always call `dr/target-ready` when loads complete
- **Handle failures**: Provide error UI and fallback routes

### Component Structure
- **Singleton routers**: Each router must have a unique ident and can appear in one place
- **Explicit state composition**: When loading parents dynamically, manually compose child router state with `:pre-merge`
- **Clean separation**: Keep route targets independent; use queries and parameters for communication

### Navigation
- **Prefer IDE-navigable routes**: Use `path-to` instead of hardcoded path vectors
- **Validate parameters**: Always coerce route parameters from strings to correct types
- **Support deep linking**: Ensure your app can reconstruct state from any valid route path
- **Test routes thoroughly**: Route logic is often an edge-case source of bugs

### Performance
- **Route immediately when possible**: Avoid unnecessary delays for instant UI feedback
- **Only load needed data**: Don't load unrelated data in deferred routes
- **Cache route data**: When returning to a route, avoid reloading if data hasn't changed
- **Consider code splitting**: Large route modules can be dynamically imported

## Limitations and Gotchas

- **Routers are singletons**: A given `defrouter` can only appear once in the UI tree
- **Partial routes create ambiguity**: Routing to `["settings"]` without specifying a nested route can leave nested routers uninitialized
- **Will-enter is idempotent**: It may be called multiple times; never side-effect, only in the `route-deferred` lambda
- **Parameters are strings**: Always coerce route parameters from their string representation
- **Order matters in targets**: The first target listed in `router-targets` is the default for initial state
