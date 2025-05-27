# Advanced Topics

## Overview
This chapter covers advanced Fulcro concepts including code splitting, logging, production debugging, React Native integration, and GraphQL interoperability.

## Code Splitting

### Shadow-cljs Module Configuration
```clojure
;; shadow-cljs.edn
{:builds
 {:main
  {:target :browser
   :modules
   {:main     {:init-fn app.client/init
               :entries [app.client]}
    :admin    {:depends-on #{:main}
               :entries [app.admin]}
    :reports  {:depends-on #{:main}
               :entries [app.reports]}
    :vendor   {:entries [react react-dom]
               :depends-on #{}}}}}}
```

### Dynamic Loading in Router
```clojure
(defsc AdminPage [this props]
  {:route-segment ["admin"]
   :will-enter
   (fn [app params]
     (-> (js/import "./admin.js")
         (.then
           (fn [module]
             ;; Module loaded, can now route
             (dr/route-immediate [:component/id ::AdminPage])))
         (.catch
           (fn [error]
             (log/error "Failed to load admin module" error)
             (dr/route-immediate [:component/id ::ErrorPage])))))}
  (dom/div "Admin Interface"))
```

### Component-Level Code Splitting
```clojure
(defsc LazyChart [this props]
  {:componentDidMount
   (fn [this]
     (when-not @chart-lib-loaded?
       (-> (js/import "chart.js")
           (.then (fn [Chart]
                    (reset! chart-lib-loaded? true)
                    (comp/force-update! this))))))}
  (if @chart-lib-loaded?
    (ui-chart props)
    (dom/div "Loading chart...")))
```

## Logging

### Logging Configuration
```clojure
(ns app.logging
  (:require
    [taoensso.timbre :as log]))

;; Configure logging levels and outputs
(log/set-config!
  {:level     :info
   :appenders {:console (log/console-appender)
               :file    (when-not js/goog.DEBUG
                          (log/spit-appender {:fname "app.log"}))}
   :middleware [(fn [data]
                  ;; Add request ID to all log entries
                  (assoc data :request-id (get-request-id)))]})
```

### Structured Logging
```clojure
(defn log-user-action [user-id action details]
  (log/info "User action"
            {:user-id user-id
             :action action
             :details details
             :timestamp (js/Date.now)
             :session-id (get-session-id)}))

;; Usage
(log-user-action 123 :document-created {:doc-id 456 :doc-type :report})
```

### Performance Logging
```clojure
(defmacro with-timing [operation-name & body]
  `(let [start# (js/performance.now)]
     (try
       ~@body
       (finally
         (let [duration# (- (js/performance.now) start#)]
           (log/debug "Operation timing"
                      {:operation ~operation-name
                       :duration-ms duration#}))))))

;; Usage
(with-timing "complex-calculation"
  (perform-complex-calculation data))
```

## Production Debug

### Debug Information in Builds
```clojure
;; shadow-cljs.edn production debugging
{:builds
 {:main
  {:target :browser
   :compiler-options {:source-map true
                      :source-map-include-sources-content true}
   :closure-defines {goog.DEBUG false
                     app.config/DEBUG_ENABLED true}}}}
```

### Remote Debug Access
```clojure
(ns app.debug
  (:require [com.fulcrologic.fulcro.inspect.inspect-client :as inspect]))

(when (and js/goog.DEBUG (= "true" js/process.env.REMOTE_DEBUG))
  ;; Enable remote inspect connection
  (inspect/app-started! app {:remote-inspector-url "ws://localhost:8237/ws"}))
```

### Production Error Reporting
```clojure
(defn setup-error-reporting []
  (set! js/window.onerror
        (fn [message source lineno colno error]
          (log/error "Global error"
                     {:message message
                      :source source
                      :line lineno
                      :column colno
                      :error (str error)
                      :user-agent js/navigator.userAgent
                      :url js/location.href})
          ;; Send to error reporting service
          (report-error-to-service
            {:type "javascript-error"
             :message message
             :stack (when error (.-stack error))}))))

(defn report-fulcro-errors []
  (app/set-global-error-handler!
    (fn [error-map]
      (log/error "Fulcro error" error-map)
      (report-error-to-service error-map))))
```

## React Native Integration

### React Native Setup
```clojure
;; deps.edn
{:deps {com.fulcrologic/fulcro {:mvn/version "3.5.9"}
        com.fulcrologic/fulcro-native {:mvn/version "1.0.0"}}}

;; App component
(ns mobile.core
  (:require
    [com.fulcrologic.fulcro.application :as app]
    [com.fulcrologic.fulcro.components :as comp]
    ["react-native" :as rn]))

(defsc MobileApp [this props]
  {:query [:ui/loading?]
   :initial-state {:ui/loading? false}}
  (rn/View #js {:style #js {:flex 1}}
    (rn/Text #js {:style #js {:fontSize 20}}
             "Hello from Fulcro Native!")))

(def ui-mobile-app (comp/factory MobileApp))
```

### Native DOM Elements
```clojure
(ns mobile.components
  (:require
    ["react-native" :as rn]
    [com.fulcrologic.fulcro.components :as comp]))

(defsc UserProfile [this {:user/keys [name avatar bio]}]
  {:query [:user/name :user/avatar :user/bio]}
  (rn/View #js {:style styles/container}
    (rn/Image #js {:source #js {:uri avatar}
                   :style styles/avatar})
    (rn/Text #js {:style styles/name} name)
    (rn/Text #js {:style styles/bio} bio)))

(def styles
  #js {:container #js {:padding 20}
       :avatar    #js {:width 80 :height 80 :borderRadius 40}
       :name      #js {:fontSize 18 :fontWeight "bold"}
       :bio       #js {:fontSize 14 :color "#666"}})
```

### Navigation in React Native
```clojure
(ns mobile.navigation
  (:require
    ["@react-navigation/native" :as nav]
    ["@react-navigation/stack" :as stack]
    [com.fulcrologic.fulcro.components :as comp]))

(def Stack (stack/createStackNavigator))

(defsc AppNavigator [this props]
  (nav/NavigationContainer nil
    (comp/create-element Stack.Navigator nil
      (comp/create-element Stack.Screen
                          #js {:name "Home" :component ui-home-screen})
      (comp/create-element Stack.Screen
                          #js {:name "Profile" :component ui-profile-screen}))))
```

## GraphQL Integration

### GraphQL Remote
```clojure
(ns app.graphql
  (:require
    [com.fulcrologic.fulcro.networking.http-remote :as http]
    [edn-query-language.core :as eql]))

(defn eql->graphql [query]
  ;; Convert EQL to GraphQL query string
  ;; This is a simplified example
  (str "query { " 
       (eql/query->string query)
       " }"))

(defn graphql-remote []
  (http/fulcro-http-remote
    {:url "/graphql"
     :request-middleware
     [(fn [request]
        (update request :body
                (fn [eql-query]
                  {:query (eql->graphql eql-query)})))]}))
```

### GraphQL Query Translation
```clojure
(defn translate-eql-to-graphql [eql-ast]
  (case (:type eql-ast)
    :root
    (str "query {\n"
         (string/join "\n" (map translate-eql-to-graphql (:children eql-ast)))
         "\n}")
    
    :prop
    (name (:dispatch-key eql-ast))
    
    :join
    (str (name (:dispatch-key eql-ast)) " {\n"
         (string/join "\n" (map translate-eql-to-graphql (:children eql-ast)))
         "\n}")
    
    :union
    ;; Handle GraphQL unions/interfaces
    (str "... on " (:union-key eql-ast) " {\n"
         (string/join "\n" (map translate-eql-to-graphql (:children eql-ast)))
         "\n}")
    
    ""))
```

### GraphQL Mutations
```clojure
(defmutation create-user [params]
  (remote [env]
    ;; Convert to GraphQL mutation
    {:type :graphql-mutation
     :mutation 'createUser
     :params params
     :selection [:user/id :user/name :user/email]}))

(defn graphql-mutation-remote []
  (http/fulcro-http-remote
    {:url "/graphql"
     :request-middleware
     [(fn [request]
        (if (= :graphql-mutation (:type (:body request)))
          (let [{:keys [mutation params selection]} (:body request)]
            (assoc request :body
                   {:query (str "mutation { "
                               (name mutation) "("
                               (params->graphql-args params) ") {"
                               (selection->graphql selection)
                               "} }")}))
          request))]}))
```

## Workspaces Development

### Workspace Configuration
```clojure
;; shadow-cljs.edn
{:builds
 {:workspaces
  {:target :browser
   :output-dir "public/workspaces"
   :asset-path "/workspaces"
   :modules {:main {:init-fn app.workspaces/init}}
   :dev {:http-root "public"
         :http-port 8080}}}}
```

### Component Workspaces
```clojure
(ns app.workspaces
  (:require
    [nubank.workspaces.core :as ws]
    [nubank.workspaces.model :as wsm]
    [nubank.workspaces.card-types.fulcro3 :as ct.fulcro]
    [app.ui :as ui]))

(ws/defcard user-profile-card
  {::wsm/card-width 3 ::wsm/card-height 4}
  (ct.fulcro/fulcro-card
    {::ct.fulcro/root ui/UserProfile
     ::ct.fulcro/initial-state
     {:user/id 1
      :user/name "John Doe"
      :user/email "john@example.com"
      :user/avatar "https://via.placeholder.com/150"}}))

(ws/defcard user-form-card
  (ct.fulcro/fulcro-card
    {::ct.fulcro/root ui/UserForm
     ::ct.fulcro/initial-state
     (fs/add-form-config ui/UserForm
       {:user/name "" :user/email ""})}))

(defn init []
  (ws/mount))
```

### Interactive Development
```clojure
(ws/defcard data-explorer
  "Explore application data structure"
  (ct.fulcro/fulcro-card
    {::ct.fulcro/root ui/DataExplorer
     ::ct.fulcro/initial-state
     {:data/complex-structure
      {:users [{:id 1 :name "Alice"}
               {:id 2 :name "Bob"}]
       :posts [{:id 101 :title "First Post" :author-id 1}
               {:id 102 :title "Second Post" :author-id 2}]}}
     ::ct.fulcro/wrap-root? false}))
```

## Batched Reads

### Server-Side Batching
```clojure
(ns app.batch-processing
  (:require [com.wsscode.pathom.core :as p]))

(defn batch-resolver [batch-fn individual-fn]
  (fn [env input]
    (if (> (count input) 1)
      ;; Batch multiple requests
      (batch-fn env input)
      ;; Single request
      (individual-fn env (first input)))))

(pc/defresolver users-batch-resolver [env inputs]
  {::pc/input  #{:user/id}
   ::pc/output [:user/name :user/email]
   ::pc/batch? true}
  (let [user-ids (map :user/id inputs)]
    ;; Single database query for all users
    (load-users-by-ids user-ids)))
```

### Client-Side Batching
```clojure
;; Enable request batching in remote
(def batched-remote
  (http/fulcro-http-remote
    {:batch-requests? true
     :batch-timeout 10})) ; Wait 10ms for more requests

;; Multiple loads get batched automatically
(df/load! this :user-1 User)
(df/load! this :user-2 User)
(df/load! this :user-3 User)
;; All three requests sent in single HTTP request
```

## Best Practices

### Performance Optimization
- **Lazy load modules**: Split code at route boundaries
- **Batch database operations**: Combine multiple reads/writes
- **Cache expensive operations**: Memoize calculations
- **Profile before optimizing**: Measure actual bottlenecks

### Error Handling
- **Graceful degradation**: App should work with partial failures
- **Comprehensive logging**: Capture context for debugging
- **User-friendly errors**: Don't expose technical details
- **Error recovery**: Provide ways to retry failed operations

### Development Workflow
- **Use workspaces**: Develop components in isolation
- **Enable source maps**: Debug production issues effectively
- **Monitor performance**: Track metrics in production
- **Test on devices**: Mobile performance differs significantly

### Architecture Guidelines
- **Keep platform abstractions thin**: Don't over-abstract differences
- **Share business logic**: Platform-specific UI, shared data layer
- **Plan for offline**: Consider network reliability
- **Progressive enhancement**: Basic functionality works everywhere