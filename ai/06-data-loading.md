# Data Loading

## Loading Overview

### Core Function: `load!`
Primary mechanism for loading data from servers into normalized client database.

### Three Basic Scenarios
1. **Load to root**: Place data at application root level
2. **Load and normalize**: Fetch tree and auto-normalize into tables
3. **Target loading**: Load tree and place at specific graph location

## Server Setup for Loading

### Remote Configuration
```clojure
(ns app.application
  (:require
    [com.fulcrologic.fulcro.application :as app]
    [com.fulcrologic.fulcro.networking.http-remote :as http]))

(defonce app (app/fulcro-app
               {:remotes {:remote (http/fulcro-http-remote {})}}))
```

### Pathom Resolvers
```clojure
(ns app.resolvers
  (:require
    [com.wsscode.pathom.connect :as pc]))

;; Entity resolver
(pc/defresolver person-resolver [env {:person/keys [id]}]
  {::pc/input  #{:person/id}
   ::pc/output [:person/name :person/age]}
  (get people-table id))

;; List resolver  
(pc/defresolver list-resolver [env {:list/keys [id]}]
  {::pc/input  #{:list/id}
   ::pc/output [:list/label {:list/people [:person/id]}]}
  (when-let [list (get list-table id)]
    (assoc list :list/people (mapv (fn [id] {:person/id id}) (:list/people list)))))

;; Root resolvers
(pc/defresolver friends-resolver [env input]
  {::pc/output [{:friends [:list/id]}]}
  {:friends {:list/id :friends}})
```

## Loading Patterns

### 1. Loading to Root
```clojure
;; Load root-level properties
(df/load! app :friends PersonList)
(df/load! app :enemies PersonList)
```

Generates query: `[{:friends (comp/get-query PersonList)}]`

### 2. Loading Specific Entities (by ident)
```clojure
;; Load specific person
(df/load! this [:person/id 3] Person)
```

Generates query: `[{[:person/id 3] (comp/get-query Person)}]`

### 3. Loading with Targeting
```clojure
;; Load and append to existing list
(df/load! this [:person/id 3] Person 
  {:target (targeting/append-to [:list/id :friends :list/people])})

;; Load and replace single reference
(df/load! this [:person/id 3] Person
  {:target [:current-user]})

;; Load and prepend to list
(df/load! this [:person/id 3] Person
  {:target (targeting/prepend-to [:root/people])})
```

## Targeting Options

### Available Targeting Functions
```clojure
(require '[com.fulcrologic.fulcro.algorithms.data-targeting :as targeting])

;; Replace single value
:target [:path :to :location]

;; Append to vector (if not already present)  
:target (targeting/append-to [:path :to :vector])

;; Prepend to vector
:target (targeting/prepend-to [:path :to :vector])

;; Replace entire vector
:target (targeting/replace-at [:path :to :vector])
```

### Multiple Targets
```clojure
(df/load! this [:person/id 3] Person
  {:target [(targeting/append-to [:list/id :friends :list/people])
            [:current-selection]]})
```

## Loading Parameters

### Query Parameters
```clojure
(df/load! this :all-people Person 
  {:params {:limit 10 :offset 20}})
```

Server receives: `[(:all-people {:limit 10 :offset 20})]`

### Focus and Without
```clojure
;; Load only specific fields
(df/load! this [:person/id 3] Person
  {:focus [:person/name]})

;; Load without certain fields  
(df/load! this [:person/id 3] Person
  {:without #{:person/age}})
```

## Loading Indicators

### Marker Configuration
```clojure
(df/load! this :people Person
  {:marker :people-loading})

;; Check loading state
(df/loading? (app/current-state app) :people-loading)
```

### Global Loading State
```clojure
;; Check if any loads are active
(df/loading? (app/current-state app))
```

## Error Handling

### Error Callbacks
```clojure
(df/load! this :people Person
  {:error-action (fn [{:keys [error]}]
                   (log/error "Load failed:" error))})
```

### Post-Load Actions
```clojure
(df/load! this :people Person
  {:post-action (fn [env]
                  (log/info "Load completed"))})
```

## Advanced Loading

### Refresh After Mutation
```clojure
(defmutation save-person [params]
  (action [env] ...)
  (remote [env] (m/returning Person)))
```

### Loading in Lifecycle
```clojure
(defsc PersonList [this props]
  {:componentDidMount (fn [this]
                       (df/load! this :people Person))}
  ...)
```

### Parallel Loading
```clojure
;; Multiple loads execute in parallel
(df/load! app :friends PersonList)
(df/load! app :enemies PersonList)
(df/load! app :current-user Person)
```

## Load State Management

### Automatic Normalization
When data arrives, Fulcro automatically:
1. **Normalizes** the tree using component idents
2. **Merges** into existing database
3. **Updates** any targeted locations
4. **Triggers** UI refresh

### Manual Merge Alternative
```clojure
;; Instead of load!, manually merge data
(merge/merge-component! app Person person-data 
  :append [:list/id :friends :list/people])
```

## Loading Best Practices

### Component-Level Loading
```clojure
(defsc PersonDetail [this {:person/keys [id name] :as props}]
  {:componentDidMount 
   (fn [this]
     (when (and id (not name)) ; Only load if needed
       (df/load! this [:person/id id] PersonDetail)))}
  ...)
```

### Conditional Loading
```clojure
;; Load only if data is stale or missing
(when (> (- (js/Date.now) last-refresh) 300000) ; 5 minutes
  (df/load! this :current-data SomeComponent))
```

### Loading with Dependencies
```clojure
;; Load person, then their addresses
(df/load! this [:person/id person-id] Person
  {:post-action 
   (fn [{:keys [app]}]
     (df/load! app :addresses Address 
       {:params {:person-id person-id}}))})
```