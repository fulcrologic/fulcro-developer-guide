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

**Important**: You MUST NOT mix raw and standard namespaces. Use either raw or standard versions consistently.

## Normalizing Components

### The Problem with React-Free Environments

`defsc` generates React components, making it unusable in non-React environments. Fulcro Raw provides `rc/nc` (normalizing component) as an alternative.

### Basic Normalizing Component

```clojure
;; Simple component with automatic ident generation
(def Person (rc/nc [:person/id :person/name {:person/address [:address/id :address/street]}]))
```

This generates **two** anonymous components with:
- Complete query and ident functions
- Automatic ident detection (any attribute named `id` becomes the table name and ID field)

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
   :initial-state (fn [_] {...})})
```

### Component Usage Examples

```clojure
;; Concise network loading
(df/load! app [:person/id 42] (rc/nc [:person/name :person/age]))

;; Data manipulation without React
(merge/merge-component! app Person {:person/id 1 :person/name "Bob"})

;; Query execution
(comp/get-query Person) ; => [:person/id :person/name {:person/address [:address/id :address/street]}]
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
- Supports custom rendering systems if needed

### Data Subscriptions

Raw applications provide `add-component!` and `remove-component!` for watching data changes:

```clojure
;; Subscribe to component props changes
(rapp/add-component! app Person {:person/id 42}
  (fn [component props]
    (println "Person data changed:" props)))

;; Cleanup subscription
(rapp/remove-component! app Person {:person/id 42})
```

### Complete Example

```clojure
(ns my-app.raw-demo
  (:require [com.fulcrologic.fulcro.raw.application :as rapp]
            [com.fulcrologic.fulcro.raw.components :as rc]
            [com.fulcrologic.fulcro.data-fetch :as df]))

;; Define data components
(def User (rc/nc [:user/id :user/name :user/email]))
(def Post (rc/nc [:post/id :post/title :post/content {:post/author User}]))

;; Create raw app
(def app (rapp/fulcro-app {:remotes {:remote (http-remote/fulcro-http-remote {:url "/api"})}}))

;; Subscribe to user data changes
(rapp/add-component! app User {:user/id 1}
  (fn [component props]
    (println "User updated:" (:user/name props))))

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
- `com.fulcrologic.fulcro.ui-state-machines` (with `componentName` option)
- All raw versions of standard namespaces

### Incompatible Namespaces

DOM and browser-specific namespaces don't work:

- `com.fulcrologic.fulcro.dom*`
- `com.fulcrologic.fulcro.routing.dynamic-routing`
- `com.fulcrologic.fulcro.networking.file-upload`
- `com.fulcrologic.fulcro.networking.file-url`
- `com.fulcrologic.fulcro.react.*`
- `com.fulcrologic.fulcro.rendering.*`
- Standard `application` and `components` namespaces

## React Hooks Integration

### Using Raw APIs in Standard Fulcro

Raw namespaces work perfectly in normal Fulcro applications, enabling hybrid approaches.

### Hooks Components

```clojure
(ns my-app.hooks
  (:require [com.fulcrologic.fulcro.react.hooks :as hooks]))

(defn UserDisplay [props]
  (let [user-data (hooks/use-component app User {:user/id (:user-id props)})]
    (dom/div
      (dom/h3 (:user/name user-data))
      (dom/p (:user/email user-data)))))
```

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
- Creates a random UUID on mount
- Cleans up associated state on unmount
- Enables truly dynamic component lifecycles

## UI State Machines with Raw/Hooks

### Raw UISM Usage

```clojure
(def login-machine
  (uism/state-machine
    {::uism/actors #{:actor/login-form}
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

## Advanced Patterns

### Data-Only Processing

```clojure
(ns data-processor
  (:require [com.fulcrologic.fulcro.raw.application :as rapp]
            [com.fulcrologic.fulcro.raw.components :as rc]))

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
            [seesaw.core :as seesaw]))

(def app (rapp/fulcro-app {}))
(def UserList (rc/nc [:user/id :user/name]))

;; Subscribe to data changes and update Swing UI
(rapp/add-component! app UserList {}
  (fn [component users]
    (seesaw/config! user-list-widget 
                    :model (map :user/name users))))

;; Load data (will update UI via subscription)
(df/load! app :all-users UserList)
```

## Best Practices

1. **Use Consistent Namespaces**: Don't mix raw and standard namespaces
2. **Component Names for Dynamic Queries**: Use `:componentName` option when needed
3. **Subscription Cleanup**: Always clean up subscriptions to prevent memory leaks
4. **Hook Dependencies**: Be careful with hook dependencies in React integration
5. **State Management**: Use Fulcro's transaction system even in raw mode
6. **Error Handling**: Raw applications still support Fulcro's error handling patterns

## Use Cases

### Perfect For:
- Data processing applications
- Desktop UIs with JVM libraries
- Alternative rendering systems
- Microservices with shared data models
- React integration where full Fulcro control isn't needed

### Not Ideal For:
- Standard web applications (use regular Fulcro)
- Simple data transformation (native Clojure might be better)
- Applications requiring DOM manipulation

Fulcro Raw provides a powerful foundation for data-centric applications while maintaining all the benefits of Fulcro's normalized database, query system, and transaction processing.