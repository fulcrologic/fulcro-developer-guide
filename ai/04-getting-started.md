
# Getting Started

## Prerequisites

### Install Fulcro Inspect
**Essential Chrome extension** for development:
- View internal application state
- Transaction history
- State snapshots and time travel
- Query autocomplete with Pathom
- Source-level debugging

### Chrome Development Settings
Enable in Chrome DevTools settings:
- **Console**: "Enable Custom Formatters"
- **Network**: "Disable Cache (while devtools is open)"

### Required Tools
- **Java SE Development Kit**: Version 8 recommended (OpenJDK or official)
- **Clojure CLI Tools**: For dependency management
- **Node.js and npm**: For ClojureScript compilation
- **Editor**: IntelliJ CE + Cursive (recommended) or Emacs/Spacemacs

## Project Setup

### Directory Structure
```bash
mkdir app && cd app
mkdir -p src/main src/dev resources/public
npm init
npm install shadow-cljs react react-dom --save
```

### Dependencies (`deps.edn`)
```clojure
{:paths   ["src/main" "resources"]
 :deps    {org.clojure/clojure    {:mvn/version "1.10.3"}
           com.fulcrologic/fulcro {:mvn/version "3.5.9"}}

 :aliases {:dev {:extra-paths ["src/dev"]
                 :extra-deps  {org.clojure/clojurescript   {:mvn/version "1.10.914"}
                               thheller/shadow-cljs        {:mvn/version "2.16.9"}
                               binaryage/devtools          {:mvn/version  "1.0.4"}}}}}
```

### Shadow-cljs Configuration (`shadow-cljs.edn`)
```clojure
{:deps     {:aliases [:dev]}
 :dev-http {8000 "classpath:public"}
 :builds   {:main {:target     :browser
                   :output-dir "resources/public/js/main"
                   :asset-path "/js/main"
                   :dev        {:compiler-options {:external-config {:fulcro {:html-source-annotations? true}}}}
                   :modules    {:main {:init-fn app.client/init
                                       :entries [app.client]}}
                   :devtools   {:after-load app.client/refresh
                                :preloads   [com.fulcrologic.fulcro.inspect.preload
                                             com.fulcrologic.fulcro.inspect.dom-picker-preload]}}}}
```

Note: The `:dev` compiler option enables source annotations that add `data-fulcro-source` attributes to DOM elements, making it easier to trace UI back to source code during development.

### HTML File (`resources/public/index.html`)
```html
<html>
 <meta charset="utf-8">
  <body>
    <div id="app"></div>
    <script src="/js/main/main.js"></script>
  </body>
</html>
```

## Basic Application Structure

### Application Setup (`src/main/app/application.cljs`)
```clojure
(ns app.application
  (:require [com.fulcrologic.fulcro.application :as app]))

(defonce app (app/fulcro-app))
```

### UI Components (`src/main/app/ui.cljs`)
```clojure
(ns app.ui
  (:require
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    [com.fulcrologic.fulcro.dom :as dom]))

(defsc Person [this {:person/keys [name age]}]
  (dom/div
    (dom/p "Name: " name)
    (dom/p "Age: " age)))

(def ui-person (comp/factory Person))

(defsc Root [this props]
  (dom/div
    (ui-person {:person/name "Joe" :person/age 22})))
```

### Client Entry Point (`src/main/app/client.cljs`)
```clojure
(ns app.client
  (:require
    [app.application :refer [app]]
    [app.ui :as ui]
    [com.fulcrologic.fulcro.application :as app]
    [com.fulcrologic.fulcro.components :as comp]))

(defn ^:export init []
  (app/mount! app ui/Root "app"))

(defn ^:export refresh []
  (app/mount! app ui/Root "app")
  (comp/refresh-dynamic-queries! app))
```

## Building and Running

### Start Shadow-cljs Server
```bash
npx shadow-cljs server
```

### Build Process
1. Navigate to http://localhost:9630 (Shadow-cljs UI)
2. Select "main" build under "Builds" menu
3. Click "start watch"
4. Access app at http://localhost:8000

### REPL Connection
```clojure
;; Connect to nREPL and select build
user=> (shadow/repl :main)
;; Test connection
cljs.user=> (js/alert "Hi")
```

## Component Development

### Component Anatomy
```clojure
(defsc ComponentName [this props]
  {:query [...] :ident [...] :initial-state {...}} ; optional
  (dom/div {:className "a" :style {:color "red"}}
    (dom/p "Hello")))
```

The `defsc` macro creates a React component with Fulcro's data management. The three main parameters are:
1. **Component name** - The class name
2. **`[this props]`** - `this` is the component instance, `props` is the data from the database
3. **Options map** (optional) - Contains `:query`, `:ident`, `:initial-state`, etc.
4. **Render body** - Returns React elements

### DOM Element Factories
```clojure
;; Various syntax forms - all equivalent:
(dom/div {:id "id" :className "x y z"} ...)
(dom/div :.x#id {:className "y z"} ...)
(dom/div :.x.y.z#id ...)
(dom/div :.x#id {:classes ["y" "z"]} ...)
```

### Factory Functions
Factory functions let you use components within your React tree. They wrap the component class and are conventionally prefixed with `ui-`:

```clojure
(def ui-person (comp/factory Person {:keyfn :person/id}))

;; Usage
(ui-person {:person/name "Joe" :person/age 22})
```

The `:keyfn` option is important for React reconciliation - it tells React how to identify elements in lists by specifying which prop/key to use as the React key.

## Data Flow Basics

### Initial State
```clojure
(defsc Person [this {:person/keys [name age]}]
  {:initial-state (fn [{:keys [name age]}] {:person/name name :person/age age})}
  (dom/div ...))

(defsc Root [this {:keys [friends]}]
  {:initial-state (fn [_] {:friends (comp/get-initial-state Person {:name "Joe" :age 22})})}
  (dom/div (ui-person friends)))
```

Initial state functions allow components to compose their initial state with their parent's state, enabling the state tree to mirror the UI tree structure.

### Queries
```clojure
(defsc Person [this {:person/keys [name age]}]
  {:query [:person/name :person/age]
   :initial-state ...}
  (dom/div ...))

(defsc Root [this {:keys [friends]}]
  {:query [{:friends (comp/get-query Person)}]}
  (dom/div (ui-person friends)))
```

Queries describe what data the component needs from the database. Parent queries join child queries, building a tree that mirrors your component structure.

### Passing Callbacks and Computed Data

When you need to pass callbacks or other computed data from parent to child, you must use `comp/computed` and accept a third parameter in `defsc`.

**Incorrect Pattern:**
```clojure
;; DON'T DO THIS - callbacks will be lost on optimized refresh
(ui-person (assoc props :onDelete delete-fn))
```

**Correct Pattern:**
```clojure
;; Define the component with a third parameter for computed props
(defsc Person [this {:person/keys [name]} {:keys [onDelete]}]
  (dom/div (dom/button {:onClick #(onDelete name)} "Delete")))

(def ui-person (comp/factory Person {:keyfn :person/id}))

;; Parent passes computed props using comp/computed
(defsc PersonList [this {:list/keys [people]}]
  (let [delete-fn (fn [id] (println "Deleting" id))]
    (dom/div
      (dom/ul
        (mapv (fn [person]
                (ui-person (comp/computed person {:onDelete delete-fn})))
          people)))))
```

Why is this necessary? Fulcro optimizes component refreshes by only querying for child data when the parent hasn't changed. If callbacks are passed as regular props, they'll be lost during these optimized refreshes because only database data gets supplied. Using `comp/computed` ensures callbacks persist through these optimizations.

You can also access computed props in the component body using `(comp/get-computed this)`.

## Hot Code Reload
- Shadow-cljs provides automatic hot reload
- Edit component code and save
- UI updates without losing state
- The `refresh` function in client.cljs handles re-mounting and query refresh
- Use `comp/refresh-dynamic-queries!` to ensure dynamic queries are updated (Fulcro 3.3.0+)
