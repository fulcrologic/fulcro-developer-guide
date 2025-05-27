# Dynamic Routing

## Overview
Fulcro's Dynamic Router provides a powerful, composable routing system that integrates seamlessly with the component graph and query system.

## Core Concepts

### Router Benefits
- **Component-based**: Routes are defined by components
- **Composable**: Nested routers work together automatically
- **Query integration**: Routes participate in the query system
- **Automatic targeting**: Route data automatically placed in database
- **Type safety**: Route parameters validated at compile time

### Router vs Legacy Routers
- **Dynamic Router**: Recommended for new applications
- **Legacy UI Routers**: Simpler but less flexible
- **Migration**: Can coexist, migrate incrementally

## Basic Router Setup

### Route Target Components
```clojure
(defsc HomePage [this {:keys [home/id]}]
  {:query         [:home/id]
   :ident         (fn [] [:component/id ::HomePage])
   :route-segment ["home"]
   :will-enter    (fn [app params] [:component/id ::HomePage])}
  (dom/div "Home Page"))

(defsc UserPage [this {:keys [user/id user/name]}]
  {:query         [:user/id :user/name]
   :ident         :user/id
   :route-segment ["user" :user-id]
   :will-enter    (fn [app {:keys [user-id]}]
                    (dr/route-deferred [:user/id (parse-long user-id)]
                      #(df/load! app [:user/id (parse-long user-id)] UserPage)))}
  (dom/div "User: " name))
```

### Router Component
```clojure
(defsc MainRouter [this {:keys [current-route]}]
  {:query         [{:current-route (comp/get-query HomePage)}]
   :ident         (fn [] [:component/id ::MainRouter])
   :initial-state {:current-route {}}}
  (case (first (comp/get-ident this {:current-route current-route}))
    :component/id (ui-home-page current-route)
    :user/id (ui-user-page current-route)
    (dom/div "Loading...")))
```

## Route Segments

### Static Segments
```clojure
:route-segment ["users"]        ; matches /users
:route-segment ["admin" "users"] ; matches /admin/users
```

### Dynamic Segments
```clojure
:route-segment ["user" :user-id]           ; matches /user/123
:route-segment ["post" :post-id "edit"]    ; matches /post/456/edit
:route-segment ["user" :user-id "post" :post-id] ; matches /user/123/post/456
```

### Parameter Types
```clojure
;; String parameters (default)
:route-segment ["user" :user-id]

;; UUID parameters
:route-segment ["user" :user-id]
:route-segment-params {:user-id :uuid}

;; Integer parameters
:route-segment ["page" :page-num]
:route-segment-params {:page-num :int}
```

## Will-Enter Hook

### Immediate Routes
```clojure
;; For static/singleton components
:will-enter (fn [app params] [:component/id ::HomePage])
```

### Deferred Routes
```clojure
;; For routes requiring data loading
:will-enter (fn [app {:keys [user-id]}]
              (dr/route-deferred [:user/id (parse-long user-id)]
                #(df/load! app [:user/id (parse-long user-id)] UserPage
                   {:post-mutation        `dr/target-ready
                    :post-mutation-params {:target [:user/id (parse-long user-id)]}})))
```

### Conditional Routing
```clojure
:will-enter (fn [app params]
              (if (logged-in? app)
                [:component/id ::DashboardPage]
                (dr/route-immediate [:component/id ::LoginPage])))
```

## Navigation

### Programmatic Navigation
```clojure
;; Change route
(dr/change-route this ["user" user-id])

;; Change route with parameters
(dr/change-route this ["search"] {:q "clojure"})

;; Replace current route (no history entry)
(dr/change-route! this ["user" user-id] {:replace? true})
```

### Route Links
```clojure
;; Using route-link
(dr/route-link this ["user" user-id] {} "View User")

;; Manual link
(dom/a {:href (dr/path-to ["user" user-id])}
  "View User")
```

## Nested Routing

### Nested Router Setup
```clojure
(defsc UserRouter [this {:keys [current-route]}]
  {:query         [{:current-route (comp/get-query UserProfile)}]
   :ident         (fn [] [:component/id ::UserRouter])
   :initial-state {:current-route {}}}
  (ui-user-profile current-route))

(defsc UserProfile [this props]
  {:query         [:user/id :user/name {:user/router (comp/get-query UserRouter)}]
   :ident         :user/id
   :route-segment ["user" :user-id]
   :will-enter    user-will-enter}
  (dom/div
    (dom/h1 (:user/name props))
    (ui-user-router (:user/router props))))

(defsc UserSettings [this props]
  {:query         [:user/id :user/email]
   :ident         :user/id
   :route-segment ["settings"]
   :will-enter    (fn [app params]
                    ;; Inherit user-id from parent route
                    (dr/route-deferred (dr/current-route app this)
                      #(df/load! app (dr/current-route app this) UserSettings)))}
  (dom/div "User Settings"))
```

### Route Composition
```clojure
;; Routes compose automatically:
;; /user/123        -> UserProfile
;; /user/123/settings -> UserProfile + UserSettings nested
```

## Route Lifecycle

### Route Transitions
1. **URL Change**: Browser URL or programmatic navigation
2. **Route Resolution**: Find matching route segments
3. **Will-Enter**: Execute will-enter hooks from root to leaf
4. **Data Loading**: Any deferred routes trigger loads
5. **Target Ready**: Route becomes active when data arrives
6. **Component Render**: UI updates with new route

### Route Hooks
```clojure
:will-enter    ; Called when route is being entered
:will-leave    ; Called when route is being left
:route-cancelled ; Called if route navigation is cancelled
```

## Query Integration

### Router Queries
```clojure
(defsc MainRouter [this props]
  {:query (fn [] {:home-page    (comp/get-query HomePage)
                  :user-page    (comp/get-query UserPage)
                  :settings-page (comp/get-query SettingsPage)})
   :ident (fn [] [:component/id ::MainRouter])}
  ;; Router renders current target based on route
  )
```

### Route Data Targeting
```clojure
;; Data automatically targeted to route ident
:will-enter (fn [app {:keys [user-id]}]
              (dr/route-deferred [:user/id user-id]
                #(df/load! app [:user/id user-id] UserPage
                   {:target [:user/id user-id]}))) ; automatic targeting
```

## Error Handling

### Route Not Found
```clojure
(defsc NotFoundPage [this props]
  {:query         [:not-found/id]
   :ident         (fn [] [:component/id ::NotFoundPage])
   :route-segment ["**"] ; catch-all route
   :will-enter    (fn [app params] [:component/id ::NotFoundPage])}
  (dom/div "Page not found"))
```

### Failed Data Loading
```clojure
:will-enter (fn [app {:keys [user-id]}]
              (dr/route-deferred [:user/id user-id]
                #(df/load! app [:user/id user-id] UserPage
                   {:error-action (fn [env]
                                    (dr/change-route app ["not-found"]))})))
```

## Advanced Features

### Route Guards
```clojure
(defn require-auth [app params]
  (if (authenticated? app)
    params
    (do
      (dr/change-route app ["login"])
      nil)))

:will-enter (fn [app params]
              (when-let [params (require-auth app params)]
                [:component/id ::ProtectedPage]))
```

### Route Parameters
```clojure
;; Query parameters
(dr/change-route this ["search"] {:q "clojure" :page 2})
;; Results in /search?q=clojure&page=2

;; Access in component
(defsc SearchPage [this props]
  {:will-enter (fn [app params]
                 (let [query-params (dr/get-route-params app)]
                   ;; Use query-params for search
                   [:component/id ::SearchPage]))}
  ...)
```

### Route Aliases
```clojure
;; Define route aliases for easier navigation
(dr/register-route-aliases! app
  {:user-profile ["user" :user-id]
   :user-settings ["user" :user-id "settings"]})

;; Use aliases
(dr/change-route-to-alias this :user-profile {:user-id 123})
```

## Best Practices

### Route Design
- **Keep routes shallow**: Avoid deep nesting when possible
- **Use meaningful segments**: `/users/123/edit` vs `/u/123/e`
- **Plan for parameters**: Consider what data routes need
- **Handle edge cases**: Not found, unauthorized, etc.

### Data Loading
- **Load in will-enter**: Don't load in component lifecycle
- **Use route-deferred**: For routes requiring server data
- **Handle loading states**: Show appropriate UI during loads
- **Cache when appropriate**: Avoid unnecessary reloads

### Navigation
- **Use route-link**: For internal navigation
- **Validate parameters**: Check route params before navigation
- **Handle errors gracefully**: Failed navigation should not break app
- **Consider UX**: Loading states, back button behavior

### Performance
- **Minimize route queries**: Only include necessary data
- **Use route segments wisely**: Efficient matching patterns
- **Consider code splitting**: Large routes can be lazy loaded
- **Cache route data**: Avoid reloading unchanged data