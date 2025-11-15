
# Code Splitting and Modules

## Overview

Code splitting allows you to break your application into smaller modules that load on demand, improving initial load times and reducing bundle size. Fulcro's data-driven architecture and dynamic queries make code splitting particularly straightforward.

**Note:** This chapter covers code splitting using `com.fulcrologic.fulcro.routing.legacy-ui-routers`. While this works correctly, the Developer's Guide notes this could use updates for newer patterns.

## Core Requirements for Code Splitting

### 1. Avoid Hard Dependencies

The main application must not directly reference code in split modules:

```clojure
;; DON'T: Direct reference prevents code splitting
(ns main.app
  (:require [split-module.component :as split])) ; This breaks code splitting

;; DO: Use dynamic loading mechanisms
(ns main.app
  (:require [com.fulcrologic.fulcro.routing.legacy-ui-routers :as r]))
```

### 2. Self-Installation Mechanism

Loaded code must register itself with the application:

```clojure
;; In split module
(defmethod r/get-dynamic-router-target :my-screen [k] MyScreen)
(cljs.loader/set-loaded! :my-screen)
```

## Dynamic Routing with Code Splitting

### DynamicRouter vs Union Router

**Union Router**: All components must be available at compile time
**DynamicRouter**: Components can be loaded at runtime

```clojure
;; Union router (no code splitting)
(defsc Router [this props]
  {:query (fn [] {:main (comp/get-query Main)
                  :settings (comp/get-query Settings)}) ; All must be loaded
   :ident (fn [] [:router/by-id :singleton])}
  ...)

;; Dynamic router (enables code splitting)
(defsc Router [this props]
  {:query (fn [] (r/get-dynamic-router-query :main-router)) ; Query changes at runtime
   :ident (fn [] [:router/by-id :singleton])}
  ...)
```

### Setting Up Code Splitting with DynamicRouter

#### Step 1: Define Split Component

```clojure
;; In split module file: src/split/main_screen.cljs
(ns split.main-screen
  (:require
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    [com.fulcrologic.fulcro.routing.legacy-ui-routers :as r]
    [com.fulcrologic.fulcro.dom :as dom]
    cljs.loader))

(defsc MainScreen [this {:keys [main/title main/data]}]
  {:initial-state (fn [params] 
                    {r/dynamic-route-key :main-screen  ; Must match module name
                     :main/title "Main Screen"
                     :main/data "Loaded dynamically!"})
   :ident (fn [] [:main-screen :singleton])  ; First element matches route key
   :query [r/dynamic-route-key :main/title :main/data]}
  (dom/div
    (dom/h1 title)
    (dom/p data)))

;; Critical: Register the component
(defmethod r/get-dynamic-router-target :main-screen [k] MainScreen)

;; Critical: Mark module as loaded
(cljs.loader/set-loaded! :main-screen)
```

#### Step 2: Configure Build Modules

**Shadow-cljs config:**
```clojure
{:builds
 {:main
  {:target :browser
   :module-loader true
   :modules {:app {:init-fn main.core/init
                   :entries [main.core]}
             :main-screen {:entries [split.main-screen]
                          :depends-on #{:app}}}
   :output-dir "resources/public/js"}}}
```

**Traditional ClojureScript config:**
```clojure
{:modules {:entry-point {:output-to "js/app.js"
                        :entries #{main.core}}
           :main-screen {:output-to "js/main-screen.js"
                        :entries #{split.main-screen}}}}
```

#### Step 3: Set Up DynamicRouter

```clojure
(ns main.root
  (:require 
    [com.fulcrologic.fulcro.routing.legacy-ui-routers :as r]
    [com.fulcrologic.fulcro.components :as comp]))

(defsc Root [this {:keys [main-router]}]
  {:initial-state (fn [params]
                    {:main-router (comp/get-initial-state r/DynamicRouter {:id :main-router})})
   :query [{:main-router (r/get-dynamic-router-query :main-router)}]}
  (dom/div
    (r/ui-dynamic-router main-router)))
```

#### Step 4: Define Routing Tree

```clojure
(def routing-tree
  (r/routing-tree
    (r/make-route :home [(r/router-instruction :main-router [:home :singleton])])
    (r/make-route :main-screen [(r/router-instruction :main-router [:main-screen :singleton])])))
```

#### Step 5: Trigger Route Loading

```clojure
;; Navigation triggers automatic module loading
(comp/transact! this [(r/route-to {:handler :main-screen})])
```

## Pre-loaded Routes

Some routes may be available at startup but still use the dynamic router pattern:

```clojure
(defsc LoginScreen [this props]
  {:initial-state (fn [params] 
                    {r/dynamic-route-key :login
                     :login/form {}})
   :ident (fn [] [:login :singleton])
   :query [r/dynamic-route-key :login/form]}
  (render-login-form props))

;; Install at startup
(defn app-started! [app]
  (comp/transact! app 
    [(r/install-route {:target-kw :login :component LoginScreen})
     (r/install-route {:target-kw :signup :component SignupScreen})
     (r/route-to {:handler :login})]))

(def app
  (app/fulcro-app
    {:client-did-mount app-started!}))
```

## Complete Code Splitting Example

### Main Application

```clojure
;; src/main/core.cljs
(ns main.core
  (:require
    [com.fulcrologic.fulcro.application :as app]
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    [com.fulcrologic.fulcro.routing.legacy-ui-routers :as r]
    [com.fulcrologic.fulcro.dom :as dom]))

(def routing-tree
  (r/routing-tree
    (r/make-route :home [(r/router-instruction :app-router [:home :singleton])])
    (r/make-route :dashboard [(r/router-instruction :app-router [:dashboard :singleton])])
    (r/make-route :settings [(r/router-instruction :app-router [:settings :singleton])])))

(defsc HomeScreen [this {:keys [home/message]}]
  {:initial-state (fn [_] {r/dynamic-route-key :home :home/message "Welcome!"})
   :ident (fn [] [:home :singleton])
   :query [r/dynamic-route-key :home/message]}
  (dom/div
    (dom/h1 "Home")
    (dom/p message)
    (dom/button {:onClick #(comp/transact! this [(r/route-to {:handler :dashboard})])} "Go to Dashboard")
    (dom/button {:onClick #(comp/transact! this [(r/route-to {:handler :settings})])} "Go to Settings")))

(defsc Root [this {:keys [app-router]}]
  {:initial-state (fn [_] 
                    (merge routing-tree
                           {:app-router (comp/get-initial-state r/DynamicRouter {:id :app-router})}))
   :query [{:app-router (r/get-dynamic-router-query :app-router)} r/routing-tree-key]}
  (dom/div
    (dom/nav
      (dom/a {:href "#" :onClick #(comp/transact! this [(r/route-to {:handler :home})])} "Home")
      (dom/a {:href "#" :onClick #(comp/transact! this [(r/route-to {:handler :dashboard})])} "Dashboard") 
      (dom/a {:href "#" :onClick #(comp/transact! this [(r/route-to {:handler :settings})])} "Settings"))
    (r/ui-dynamic-router app-router)))

;; Install pre-loaded routes
(defn init []
  (let [app (app/fulcro-app {})]
    (comp/transact! app [(r/install-route {:target-kw :home :component HomeScreen})])
    (app/mount! app Root "app")))

(defmethod r/get-dynamic-router-target :home [k] HomeScreen)
```

### Split Module: Dashboard

```clojure
;; src/split/dashboard.cljs
(ns split.dashboard
  (:require
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    [com.fulcrologic.fulcro.routing.legacy-ui-routers :as r]
    [com.fulcrologic.fulcro.dom :as dom]
    cljs.loader))

(defsc DashboardScreen [this {:keys [dashboard/stats dashboard/charts]}]
  {:initial-state (fn [_] 
                    {r/dynamic-route-key :dashboard
                     :dashboard/stats {:users 150 :revenue 25000}
                     :dashboard/charts ["Chart 1" "Chart 2"]})
   :ident (fn [] [:dashboard :singleton])
   :query [r/dynamic-route-key :dashboard/stats :dashboard/charts]}
  (dom/div
    (dom/h1 "Dashboard")
    (dom/div
      (dom/h2 "Statistics")
      (dom/p (str "Users: " (:users stats)))
      (dom/p (str "Revenue: $" (:revenue stats))))
    (dom/div
      (dom/h2 "Charts")
      (dom/ul
        (map #(dom/li {:key %} %) charts)))))

(defmethod r/get-dynamic-router-target :dashboard [k] DashboardScreen)
(cljs.loader/set-loaded! :dashboard)
```

### Split Module: Settings

```clojure
;; src/split/settings.cljs
(ns split.settings
  (:require
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    [com.fulcrologic.fulcro.routing.legacy-ui-routers :as r]
    [com.fulcrologic.fulcro.dom :as dom]
    cljs.loader))

(defsc SettingsScreen [this {:keys [settings/theme settings/notifications]}]
  {:initial-state (fn [_]
                    {r/dynamic-route-key :settings
                     :settings/theme "dark"
                     :settings/notifications true})
   :ident (fn [] [:settings :singleton])
   :query [r/dynamic-route-key :settings/theme :settings/notifications]}
  (dom/div
    (dom/h1 "Settings")
    (dom/div
      (dom/label "Theme: ")
      (dom/select {:value theme :onChange #(comp/transact! this [(update-theme {:theme (.. % -target -value)})])}
        (dom/option {:value "light"} "Light")
        (dom/option {:value "dark"} "Dark")))
    (dom/div
      (dom/label
        (dom/input {:type "checkbox" :checked notifications})
        "Enable notifications"))))

(defmethod r/get-dynamic-router-target :settings [k] SettingsScreen)
(cljs.loader/set-loaded! :settings)
```

## Code Splitting and Server-Side Rendering

**Note**: This section has not been fully updated for Fulcro 3.x. The basic approach should work but may need adjustments.

### Multi-Module SSR Setup

```clojure
(ns ssr.server
  (:require 
    [com.fulcrologic.fulcro.server-render :as ssr]
    [split.dashboard :as dashboard]
    [split.settings :as settings]
    [com.fulcrologic.fulcro.components :as comp]
    [com.fulcrologic.fulcro.algorithms.denormalize :as fdn]
    [com.fulcrologic.fulcro.routing.legacy-ui-routers :as r]))

(defn build-ui-tree [route-match]
  (let [client-db (ssr/build-initial-state (comp/get-initial-state Root {}) Root)
        final-db (-> client-db
                     ;; CRITICAL: Install all routes for SSR
                     (r/install-route* :home HomeScreen)
                     (r/install-route* :dashboard dashboard/DashboardScreen)
                     (r/install-route* :settings settings/SettingsScreen)
                     (r/route-to* route-match))]
    ;; CRITICAL: Pass the final DB to get-query to get updated dynamic queries
    (fdn/db->tree (comp/get-query Root final-db) final-db final-db)))

(defn render-page [route-match]
  (let [ui-tree (build-ui-tree route-match)
        html (dom/render-to-str ((comp/factory Root) ui-tree))]
    (str "<html><body><div id='app'>" html "</div></body></html>")))
```

## Best Practices

### 1. Module Organization

```clojure
;; Group related functionality
src/
  main/           ; Core application
  modules/
    dashboard/    ; Dashboard module
    admin/        ; Admin module  
    reports/      ; Reports module
```

### 2. Lazy Loading Indicators

```clojure
(defsc LoadingScreen [this _]
  {:query []}
  (dom/div
    {:className "loading-screen"}
    (dom/div "Loading module...")
    (dom/div {:className "spinner"})))

;; Show loading while module loads
(defmethod r/get-dynamic-router-target :loading [k] LoadingScreen)
```

### 3. Error Handling

```clojure
(defn handle-load-error [error]
  (js/console.error "Failed to load module:" error)
  ;; Show error state or retry mechanism
  )
```

### 4. Preload Critical Routes

```clojure
;; Preload important modules during idle time
(defn preload-modules []
  (when (and js/requestIdleCallback 
             (not (mobile-device?)))
    (js/requestIdleCallback
      #(do 
         (cljs.loader/load :dashboard)
         (cljs.loader/load :settings)))))
```

### 5. Module Dependencies

```clojure
;; In shadow-cljs.edn
{:modules
 {:app {:init-fn main.core/init
        :entries [main.core]}
  :shared {:entries [shared.components]} ; Common components
  :dashboard {:entries [split.dashboard]
             :depends-on #{:app :shared}}
  :admin {:entries [split.admin]
         :depends-on #{:app :shared}}}}
```

## Common Pitfalls

### 1. Circular Dependencies

```clojure
;; DON'T: Main app referring to split module
(ns main.app
  (:require [split.dashboard :as dash])) ; Breaks code splitting

;; DO: Use dynamic loading
(ns main.app)
(defmethod r/get-dynamic-router-target :dashboard [k] 
  ;; Component is loaded dynamically
  )
```

### 2. Forgetting Module Registration

```clojure
;; ALWAYS include these in split modules
(defmethod r/get-dynamic-router-target :my-route [k] MyComponent)
(cljs.loader/set-loaded! :my-route)
```

### 3. Incorrect Route Keys

```clojure
;; Route key, ident first element, and module name must match
{r/dynamic-route-key :dashboard}     ; Route key
[:dashboard :singleton]              ; Ident first element
(cljs.loader/set-loaded! :dashboard) ; Module name
```

### 4. Missing routing-tree-key in Query

```clojure
;; When merging routing-tree into initial state, include r/routing-tree-key in query
(defsc Root [this props]
  {:initial-state (fn [_] (merge routing-tree {...}))
   :query [{:app-router ...} r/routing-tree-key]} ; Don't forget this!
  ...)
```

### 5. Incorrect Navigation Syntax

```clojure
;; DON'T: This function doesn't exist
(r/route-to! this :dashboard)

;; DO: Use the mutation form
(comp/transact! this [(r/route-to {:handler :dashboard})])
```

Code splitting in Fulcro applications provides significant performance benefits while maintaining the framework's data-driven architecture and component co-location principles. By following the DynamicRouter pattern, modules are automatically loaded when routes are accessed, creating a seamless user experience with improved initial load times.
