# Custom Type Support in Fulcro

## Overview

Fulcro 3.3.6+ provides centralized custom type support through a global type registry, making it easier to work with complex data types across the entire application stack. This eliminates the need to manually pass Transit options throughout your codebase.

## Evolution from Per-Function to Global Registry

### The Old Way (Pre-3.3.5)

Previously, custom types required passing Transit options to every function:

```clojure
;; Had to remember to pass options everywhere
(df/load! this :users User {:transit-options my-handlers})
(comp/transact! this [(save-user user)] {:transit-options my-handlers})
```

### The New Way (3.3.5+)

Now you install types once globally and they work everywhere:

```clojure
;; Install once at application startup
(custom-types/install!)

;; Use everywhere without additional configuration
(df/load! this :users User)
(comp/transact! this [(save-user user)])
```

## Core API

### Type Handler Creation

```clojure
(ns my-app.custom-types
  (:require
    [com.fulcrologic.fulcro.algorithms.transit :as transit]))

(deftype Point [x y])

(defn install! []
  (transit/install-type-handler!
    (transit/type-handler Point "geo/point"
      ;; Serialization function: type -> representation
      (fn [^Point p] [(.-x p) (.-y p)])
      ;; Deserialization function: representation -> type
      (fn [[x y]] (Point. x y)))))
```

### Key Components

1. **Type**: The actual type you want to serialize (e.g., `Point`)
2. **Tag**: Globally unique string identifier (e.g., `"geo/point"`)
3. **Write Transform**: Function converting type to Transit-compatible data
4. **Read Transform**: Function converting data back to type

## Practical Examples

### Simple Custom Type

```clojure
(deftype UserId [value])

(defn user-id [id]
  (UserId. id))

(defn install-user-id! []
  (transit/install-type-handler!
    (transit/type-handler UserId "app/user-id"
      (fn [^UserId uid] (.-value uid))
      (fn [value] (UserId. value)))))

;; Usage
(def user (user-id "user-123"))
;; Automatically serializes over the wire as "user-123"
;; Deserializes back to UserId instance
```

### Date/Time Types

```clojure
#?(:clj
   (defn install-date-types! []
     (transit/install-type-handler!
       (transit/type-handler java.time.LocalDate "time/local-date"
         (fn [date] (.toString date))
         (fn [date-str] (java.time.LocalDate/parse date-str))))
     (transit/install-type-handler!
       (transit/type-handler java.time.Instant "time/instant"
         (fn [instant] (.toString instant))
         (fn [instant-str] (java.time.Instant/parse instant-str)))))
   :cljs
   (defn install-date-types! []
     (transit/install-type-handler!
       (transit/type-handler js/Date "time/date"
         (fn [date] (.toISOString date))
         (fn [date-str] (js/Date. date-str))))))
```

### Money/Currency Types

```clojure
(deftype Money [amount currency])

(defn money [amount currency]
  (Money. amount currency))

(defn install-money! []
  (transit/install-type-handler!
    (transit/type-handler Money "finance/money"
      (fn [^Money m] {:amount (.-amount m) :currency (.-currency m)})
      (fn [{:keys [amount currency]}] (Money. amount currency)))))

;; Usage in mutations
(defmutation update-price [product-data]
  (action [{:keys [state]}]
    (let [new-price (money 29.99 "USD")]  ; Will serialize automatically
      (swap! state assoc-in [:product/id (:product/id product-data) :product/price] new-price))))
```

### Nested Custom Types

You can nest custom types within each other:

```clojure
(deftype Point [x y])
(deftype Rectangle [^Point upper-left ^Point lower-right])

(defn install-geometry! []
  ;; Install Point first
  (transit/install-type-handler!
    (transit/type-handler Point "geo/point"
      (fn [^Point p] [(.-x p) (.-y p)])
      (fn [[x y]] (Point. x y))))
  
  ;; Rectangle can use Point in its representation
  (transit/install-type-handler!
    (transit/type-handler Rectangle "geo/rectangle"
      (fn [^Rectangle r] [(.-upper-left r) (.-lower-right r)])
      (fn [[ul lr]] (Rectangle. ul lr)))))

;; Usage
(def rect (Rectangle. (Point. 0 0) (Point. 10 10)))
;; Automatically handles nested serialization
```

## Integration Points

### Automatic Support

Custom types work automatically with:

- **HTTP Remote**: Network requests and responses
- **WebSocket Remote**: Real-time communication (v3.2.0+)  
- **Fulcro RAD**: Rapid Application Development
- **Fulcro Inspect**: Development debugging tools
- **String Conversion**: `transit/transit-clj->str` utilities

### Example: HTTP Remote

```clojure
;; Define custom type
(deftype ProductId [id])

;; Install handler
(transit/install-type-handler!
  (transit/type-handler ProductId "ecommerce/product-id"
    (fn [^ProductId p] (.-id p))
    (fn [id] (ProductId. id))))

;; Use in mutations - automatically serializes over HTTP
(defmutation load-product [product-id]  ; ProductId instance
  (remote [env] true))  ; Sends as string, receives as ProductId

;; Use in components
(defsc Product [this {:keys [product/id product/name]}]  ; id is ProductId
  {:query [:product/id :product/name]}
  (dom/div (str "Product: " (.-id id) " - " name)))
```

## Installation and Timing

### Critical Timing

**Install types as early as possible** - some libraries close over the type registry during initialization:

```clojure
(ns my-app.main
  (:require 
    [my-app.custom-types :as types]
    [com.fulcrologic.fulcro.application :as app]))

(defn init! []
  ;; FIRST: Install custom types
  (types/install!)
  
  ;; THEN: Create application and other components
  (def app (app/fulcro-app {}))
  (app/mount! app Root "app"))
```

### Startup Configuration

```clojure
(ns my-app.custom-types)

(defn install! []
  (install-user-id!)
  (install-date-types!)
  (install-money!)
  (install-geometry!)
  (js/console.log "Custom types installed"))

;; In main entry point
(defn ^:export init []
  (custom-types/install!)  ; Before anything else
  (start-app!))
```

## Best Practices

### 1. Use deftype, not defrecord

```clojure
;; GOOD: deftype
(deftype Point [x y])

;; AVOID: defrecord (can lose type information)
(defrecord Point [x y])  ; Looks like map to Fulcro internals
```

### 2. Globally Unique Tags

```clojure
;; GOOD: Namespace-prefixed tags
"mycompany.geo/point"
"myapp.finance/money"
"ecommerce.inventory/product-id"

;; AVOID: Generic tags
"point"
"money" 
"id"
```

### 3. Simple Representations

```clojure
;; GOOD: Use simple, Transit-compatible data
(transit/type-handler Point "geo/point"
  (fn [p] [(.-x p) (.-y p)])     ; Simple vector
  (fn [[x y]] (Point. x y)))

;; GOOD: Use maps for complex data  
(transit/type-handler User "app/user"
  (fn [u] {:id (.-id u) :name (.-name u)})  ; Simple map
  (fn [{:keys [id name]}] (User. id name)))
```

### 4. Error Handling

```clojure
(defn safe-install-type! [type tag write-fn read-fn]
  (try
    (transit/install-type-handler!
      (transit/type-handler type tag write-fn read-fn))
    (catch :default e
      (js/console.error "Failed to install type handler:" tag e))))
```

### 5. Development vs Production

```clojure
(defn install! []
  (install-core-types!)
  #?(:cljs
     (when goog.DEBUG
       (install-debug-types!)
       (js/console.log "Debug types installed"))))
```

## Common Use Cases

### Entity IDs

```clojure
(deftype EntityId [table id])

(defn entity-id [table id]
  (EntityId. table id))

(transit/install-type-handler!
  (transit/type-handler EntityId "app/entity-id"
    (fn [^EntityId eid] [(.-table eid) (.-id eid)])
    (fn [[table id]] (EntityId. table id))))

;; Usage
(def user-id (entity-id :user 123))
(def product-id (entity-id :product "abc-123"))
```

### Validation Results

```clojure
(deftype ValidationResult [valid? errors])

(defn validation-result [valid? errors]
  (ValidationResult. valid? errors))

(transit/install-type-handler!
  (transit/type-handler ValidationResult "app/validation"
    (fn [^ValidationResult vr] {:valid? (.-valid? vr) :errors (.-errors vr)})
    (fn [{:keys [valid? errors]}] (ValidationResult. valid? errors))))
```

### Geographic Data

```clojure
(deftype LatLng [lat lng])
(deftype Address [street city state zip lat-lng])

(defn install-geo-types! []
  (transit/install-type-handler!
    (transit/type-handler LatLng "geo/lat-lng"
      (fn [^LatLng ll] [(.-lat ll) (.-lng ll)])
      (fn [[lat lng]] (LatLng. lat lng))))
  
  (transit/install-type-handler!
    (transit/type-handler Address "geo/address"
      (fn [^Address a] {:street (.-street a)
                       :city (.-city a) 
                       :state (.-state a)
                       :zip (.-zip a)
                       :lat-lng (.-lat-lng a)})
      (fn [{:keys [street city state zip lat-lng]}]
        (Address. street city state zip lat-lng)))))
```

## Testing Custom Types

```clojure
(deftest custom-type-serialization-test
  (let [point (Point. 10 20)
        serialized (transit/transit-clj->str point)
        deserialized (transit/transit-str->clj serialized)]
    (is (instance? Point deserialized))
    (is (= 10 (.-x deserialized)))
    (is (= 20 (.-y deserialized)))))

(deftest custom-type-network-test
  (let [product-id (ProductId. "test-123")]
    ;; Test that mutations can handle custom types
    (comp/transact! app [(save-product {:product/id product-id})])
    ;; Verify the type survives the round trip
    (is (instance? ProductId (get-in @app-state [:product/id "test-123" :product/id])))))
```

Custom types in Fulcro provide a powerful way to maintain type safety and semantic meaning throughout your application stack while working seamlessly with the framework's data-driven architecture.