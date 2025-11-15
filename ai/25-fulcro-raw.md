
# Fulcro Raw API (Version 3.5+)

## Overview

Fulcro Raw provides the full-stack data management facilities of Fulcro without React dependency. This enables using Fulcro's powerful data management with alternative rendering libraries, desktop UIs, or pure data processing applications.

## Key Benefits

- **React-Independent**: Use Fulcro's data management without React
- **Alternative Rendering**: Integrate with other UI libraries
- **Desktop Applications**: Build JVM-based desktop UIs with Fulcro
- **Data-Only Components**: Create components detached from the UI tree
- **Cleaner Architecture**: Separate data management from rendering concerns

## Core Namespaces

```clojure
(ns my-app.raw
  (:require
    [com.fulcrologic.fulcro.raw.application :as rapp]
    [com.fulcrologic.fulcro.raw.components :as rc]))
```

**Important**: You MUST NOT mix raw and standard namespaces. Use either raw or standard versions consistently throughout your application.

## Normalizing Components

### The Problem with React-Free Environments

`defsc` generates React components, making it unusable in non-React environments. Fulcro Raw provides `rc/nc` (normalizing component) as an alternative.

### Basic Normalizing Component

```clojure
;; Simple component with automatic ident generation
(def Person (rc/nc [:person/id :person/name {:person/address [:address/id :address/street]}]))
```

This generates a normalizing component with:
- Complete query and ident functions
- Automatic ident detection (any attribute named `id` becomes the table name and ID field)

The ident function will assume `:person/id` identifies the `:person/id` table, generating ident `[:person/id <id-value>]`.

### Custom Component Options

```clojure
(def Person 
  (rc/nc [:person/id :person/name {:person/address Address}]
         {:componentName ::Person
          :ident (fn [props] [:person/by-id (:person/id props)])
          :initial-state (fn [params] {:person/id 1 :person/name "Alice"})}))

(def Address
  (rc/nc [:address/id :address/street :address/city]
         {:componentName ::Address
          :ident :address/id}))
```

### Macro Version (3.6+)

```clojure
(rc/defnc Person [:person/id :person/name {:person/address [:address/id :address/street]}])

;; With options
(rc/defnc Person [:person/id :person/name] 
  {:ident :constant  ; Special ident generation
   :initial-state (fn [_] {...})
   :componentName ::Person})
```

The macro version automatically provides a component name for registration and is required for dynamic queries and UISM actors.

### Component Usage Examples

```clojure
;; Concise network loading with anonymous component
(df/load! app [:person/id 42] (rc/nc [:person/name :person/age]))

;; Data manipulation without React
(merge/merge-component! app Person {:person/id 1 :person/name "Bob"})

;; Query execution
(comp/get-query Person) ; => [:person/id :person/name {:person/address [...]}]
```

## Raw Applications

### Creating Raw Applications

```clojure
(def app (rapp/fulcro-app
           {:remotes {:remote (http-remote/fulcro-http-remote {:url "/api"})}
            :initial-state {}}))
```

The raw application constructor:
- Disables Fulcro's React rendering by default
- Provides pure data management capabilities
- Supports transaction processing, mutations, and data loading
- Does not include any rendering system

### Data Subscriptions

Raw applications provide `add-component!` and `remove-component!` for watching data changes:

```clojure
;; Subscribe to component props changes
(rapp/add-component! app Person {:person/id 42}
  {:initialize?    true
   :keep-existing? true
   :receive-props  (fn [props]
                     (println "Person data changed:" props))})

;; Cleanup subscription
(rapp/remove-component! app Person {:person/id 42})
```

The options map supports:
- `:initialize?` - When true, runs the component's `:initial-state` function on mount
- `:keep-existing?` - When true, preserves existing data in the database; when false, replaces it
- `:receive-props` - A callback function invoked whenever the component's queried props change

The callback receives the normalized props tree as defined by the component's query.

### Complete Example

```clojure
(ns my-app.raw-demo
  (:require [com.fulcrologic.fulcro.raw.application :as rapp]
            [com.fulcrologic.fulcro.raw.components :as rc]
            [com.fulcrologic.fulcro.data-fetch :as df]
            [com.fulcrologic.fulcro.algorithms.merge :as merge]
            [com.fulcrologic.fulcro.networking.http-remote :as http-remote]))

;; Define data components
(def User (rc/nc [:user/id :user/name :user/email]))
(def Post (rc/nc [:post/id :post/title :post/content {:post/author User}]))

;; Create raw app
(def app (rapp/fulcro-app {:remotes {:remote (http-remote/fulcro-http-remote {:url "/api"})}}))

;; Subscribe to user data changes
(rapp/add-component! app User {:user/id 1}
  {:initialize?    true
   :keep-existing? false
   :receive-props  (fn [props]
                     (println "User updated:" (:user/name props)))})

;; Load data (will trigger subscription)
(df/load! app [:user/id 1] User)

;; Manual data changes (will also trigger subscription)
(merge/merge-component! app User {:user/id 1 :user/name "Alice" :user/email "alice@example.com"})
```

## Supported Namespaces in Raw Mode

### Compatible Namespaces

Most Fulcro namespaces work with raw applications:

- `com.fulcrologic.fulcro.data-fetch`
- `com.fulcrologic.fulcro.mutations` 
- `com.fulcrologic.fulcro.algorithms.*`
- `com.fulcrologic.fulcro.networking.*` (except file upload/url)
- `com.fulcrologic.fulcro.ui-state-machines` (actors must use `:componentName` option)
- All raw versions of standard namespaces

### Incompatible Namespaces

DOM and browser-specific namespaces don't work in pure raw applications:

- `com.fulcrologic.fulcro.dom*`
- `com.fulcrologic.fulcro.routing.dynamic-routing`
- `com.fulcrologic.fulcro.networking.file-upload`
- `com.fulcrologic.fulcro.networking.file-url`
- `com.fulcrologic.fulcro.react.*`
- `com.fulcrologic.fulcro.rendering.*`
- Standard `application` and `components` namespaces (use raw versions instead)

## React Hooks Integration

### Using Raw APIs in Standard Fulcro

Raw namespaces work perfectly in normal Fulcro applications, enabling hybrid approaches. This allows you to use raw components and data management alongside React components.

### Hooks Components

```clojure
(ns my-app.hooks
  (:require [com.fulcrologic.fulcro.react.hooks :as hooks]
            [com.fulcrologic.fulcro.dom :as dom]))

(defn UserDisplay [props]
  (let [user-data (hooks/use-component app User {:user/id (:user-id props)})]
    (dom/div
      (dom/h3 (:user/name user-data))
      (dom/p (:user/email user-data)))))
```

The `hooks/use-component` hook connects your component to Fulcro's normalized database. It does not automatically load data—the data must already exist in the database or be loaded separately via `df/load!`.

### Complete Hooks Example

```clojure
(defn HooksUserComponent []
  (let [[user-id set-user-id] (react/useState 1)
        user-data (hooks/use-component app User {:user/id user-id})]
    (dom/div
      (dom/button 
        {:onClick #(set-user-id (inc user-id))}
        "Next User")
      (dom/div
        (dom/h3 (:user/name user-data))
        (dom/p (:user/email user-data))))))
```

The hook automatically updates when the subscribed props change, and the component will only re-render if the queried props actually differ from the previous render.

## Dynamic Lifecycle with Hooks

### Dynamic Component Creation

```clojure
(defn DynamicContainer []
  (let [[show-component set-show] (react/useState false)
        component-id (hooks/use-generated-id)]
    (dom/div
      (dom/button 
        {:onClick #(set-show (not show-component))}
        (if show-component "Hide" "Show") " Component")
      (when show-component
        (DynamicButton {:button-id component-id})))))

(defn DynamicButton [{:keys [button-id]}]
  (let [button-data (hooks/use-component app Button {:button/id button-id})]
    (dom/button
      {:onClick #(comp/transact! app [(increment-clicks {:button/id button-id})])}
      (str "Clicks: " (:button/clicks button-data 0)))))
```

The `use-generated-id` hook:
- Creates a random UUID on component mount
- Cleans up associated state on unmount automatically via `hooks/use-gc`
- Enables truly dynamic component lifecycles without manual cleanup

## UI State Machines with Raw/Hooks

### Raw UISM Usage

<!-- TODO: Verify exact callback signature for uism/add-uism! -->

```clojure
(def login-machine
  (uism/defstatemachine
    {::uism/actor-names #{:actor/login-form}
     ::uism/states
     {:initial {:events {:event/submit {:handler submit-handler :target :authenticating}}}
      :authenticating {:events {:event/success {:target :authenticated}
                               :event/failure {:target :failed}}}
      :authenticated {}
      :failed {:events {:event/retry {:target :initial}}}}}))

;; Raw usage (callback-based)
(uism/add-uism! app login-machine :login-session
  {::uism/actors {:actor/login-form LoginForm}}
  (fn [state-map]
    (let [{:keys [current-state actor/login-form]} state-map]
      (println "Login state:" current-state)
      (println "Form data:" login-form))))
```

The callback receives the current state map of the state machine, including all actor data and the current state name.

### Hooks UISM Usage

```clojure
(defn LoginComponent []
  (let [{:keys [current-state actor/login-form]} 
        (hooks/use-uism app login-machine :login-session
                       {::uism/actors {:actor/login-form LoginForm}})]
    (dom/div
      (case current-state
        :initial (dom/div "Please log in")
        :authenticating (dom/div "Authenticating...")
        :authenticated (dom/div "Welcome!")
        :failed (dom/div "Login failed, please try again"))
      (LoginFormComponent login-form))))
```

The `hooks/use-uism` hook provides reactive state machine state to your React component. The component re-renders whenever the state machine state changes.

## Advanced Patterns

### Data-Only Processing

```clojure
(ns data-processor
  (:require [com.fulcrologic.fulcro.raw.application :as rapp]
            [com.fulcrologic.fulcro.raw.components :as rc]
            [com.fulcrologic.fulcro.algorithms.merge :as merge]))

;; Pure data processing with Fulcro normalization
(def Product (rc/nc [:product/sku :product/name :product/price]))
(def Order (rc/nc [:order/id :order/total {:order/items Product}]))

(def processor-app (rapp/fulcro-app {}))

(defn process-orders [orders-data]
  ;; Normalize orders into Fulcro database
  (merge/merge-component! processor-app Order orders-data)
  
  ;; Query normalized data for analysis
  (let [db @(::app/state-atom processor-app)
        all-products (vals (:product/sku db))
        total-revenue (reduce + (map :product/price all-products))]
    {:total-products (count all-products)
     :total-revenue total-revenue}))
```

### Desktop UI Integration

```clojure
(ns desktop-app
  (:require [com.fulcrologic.fulcro.raw.application :as rapp]
            [com.fulcrologic.fulcro.raw.components :as rc]
            [com.fulcrologic.fulcro.data-fetch :as df]
            [seesaw.core :as seesaw]))

(def app (rapp/fulcro-app {}))
(def UserList (rc/nc [:user/id :user/name]))

;; Subscribe to data changes and update Swing UI
(rapp/add-component! app UserList {}
  {:receive-props (fn [users]
                    (seesaw/config! user-list-widget 
                                    :model (map :user/name users)))})

;; Load data (will update UI via subscription)
(df/load! app :all-users UserList)
```

## Best Practices

1. **Use Consistent Namespaces**: Never mix raw and standard namespaces in the same application
2. **Component Names for Dynamic Queries**: Always use `:componentName` option when your normalizing component needs to be looked up dynamically or used as a UISM actor
3. **Subscription Cleanup**: Always clean up subscriptions via `remove-component!` to prevent memory leaks
4. **Hook Dependencies**: When using hooks in React integration, be careful with dependency arrays
5. **State Management**: Use Fulcro's transaction system (`transact!`, `merge-component!`) even in raw mode for consistency
6. **Error Handling**: Raw applications fully support Fulcro's error handling patterns with remotes
7. **Data Doesn't Auto-Load**: Using `hooks/use-component` does not automatically trigger data loads—load data separately with `df/load!`

## Use Cases

### Perfect For:
- Data processing applications without UI rendering
- Desktop UIs with JVM libraries (Swing, JavaFX, etc.)
- Alternative rendering systems (Vue, Svelte, etc.)
- Microservices with shared Fulcro data models
- React integration where full Fulcro control isn't needed
- Server-side data normalization and processing

### Not Ideal For:
- Standard web applications (use regular Fulcro with React)
- Simple data transformation (native Clojure might be better)
- Applications requiring DOM manipulation (use standard Fulcro)

Fulcro Raw provides a powerful foundation for data-centric applications while maintaining all the benefits of Fulcro's normalized database, query system, and transaction processing.
