# Server-Side Rendering

## Overview
Fulcro supports server-side rendering (SSR) for improved SEO, faster initial page loads, and better user experience on slow connections.

## Core Concepts

### Benefits of SSR
- **SEO improvement**: Search engines can crawl rendered content
- **Faster perceived performance**: Users see content immediately
- **Better accessibility**: Screen readers can access initial content
- **Progressive enhancement**: Works without JavaScript

### Isomorphic Architecture
Fulcro components are written in CLJC, enabling:
- **Shared code**: Same components run client and server
- **Consistent rendering**: Identical output on both sides
- **Unified state management**: Same database format

## Basic SSR Setup

### Server-Side DOM
Fulcro provides server-side DOM functions for JVM rendering:

```clojure
(ns app.ssr
  (:require
    #?(:clj [com.fulcrologic.fulcro.dom-server :as dom]
       :cljs [com.fulcrologic.fulcro.dom :as dom])
    [com.fulcrologic.fulcro.components :as comp]))

(defsc HomePage [this {:keys [title content]}]
  {:query [:title :content]}
  (dom/div
    (dom/h1 title)
    (dom/p content)))
```

### Server-Side Rendering Function
```clojure
(ns app.server-render
  (:require
    [com.fulcrologic.fulcro.application :as app]
    [com.fulcrologic.fulcro.components :as comp]
    [com.fulcrologic.fulcro.algorithms.denormalize :as fdn]
    [app.ui :as ui]))

(defn render-page [initial-state]
  (let [app (app/fulcro-app {})
        _ (app/initialize-state! app initial-state)
        current-state @(::app/state-atom app)
        query (comp/get-query ui/Root)
        props (fdn/db->tree query current-state current-state)
        root-factory (comp/factory ui/Root)]
    ;; Returns HTML string
    (str (root-factory props))))
```

### HTML Template Integration
```clojure
(defn render-html-page [app-html initial-state]
  (str "<!DOCTYPE html>"
       "<html>"
       "<head>"
       "<meta charset=\"utf-8\">"
       "<title>My Fulcro App</title>"
       "</head>"
       "<body>"
       "<div id=\"app\">" app-html "</div>"
       "<script>window.INITIAL_STATE = " (pr-str initial-state) ";</script>"
       "<script src=\"/js/main.js\"></script>"
       "</body>"
       "</html>"))
```

## State Hydration

### Server-Side State Preparation
```clojure
(defn prepare-initial-state [request]
  (let [user-id (get-session-user-id request)
        user-data (when user-id (load-user-data user-id))
        page-data (load-page-data (:uri request))]
    ;; Normalize data for client
    (merge
      (comp/get-initial-state ui/Root {})
      {:current-user user-data
       :page-content page-data})))
```

### Client-Side Hydration
```clojure
(ns app.client
  (:require
    [com.fulcrologic.fulcro.application :as app]
    [app.ui :as ui]))

(defn ^:export init []
  (let [initial-state (or js/window.INITIAL_STATE {})
        app (app/fulcro-app {})]
    ;; Initialize with server state
    (app/initialize-state! app initial-state)
    ;; Mount without replacing server-rendered content
    (app/mount! app ui/Root "app" {:hydrate? true})))
```

## Data Loading for SSR

### Server-Side Data Resolution
```clojure
(defn resolve-route-data [route-params]
  (case (:route route-params)
    :user-profile
    (let [user-id (:user-id route-params)]
      {:user-profile (load-user-profile user-id)
       :user-posts (load-user-posts user-id)})
    
    :home
    {:featured-posts (load-featured-posts)
     :recent-activity (load-recent-activity)}
    
    {}))

(defn render-route [route-params]
  (let [route-data (resolve-route-data route-params)
        initial-state (merge
                        (comp/get-initial-state ui/Root {})
                        route-data)]
    (render-page initial-state)))
```

### Async Data Loading
```clojure
(defn async-render-route [route-params callback]
  (go
    (let [user-data (<! (async-load-user (:user-id route-params)))
          posts-data (<! (async-load-posts (:user-id route-params)))
          initial-state (merge
                          (comp/get-initial-state ui/Root {})
                          {:user-profile user-data
                           :user-posts posts-data})]
      (callback (render-page initial-state)))))
```

## Ring Integration

### SSR Middleware
```clojure
(ns app.middleware.ssr
  (:require
    [app.server-render :as ssr]
    [clojure.string :as str]))

(defn wrap-ssr [handler]
  (fn [request]
    (if (and (= :get (:request-method request))
             (accepts-html? request))
      ;; Render server-side
      (let [route-params (parse-route request)
            initial-state (ssr/prepare-initial-state request)
            app-html (ssr/render-page initial-state)
            full-html (ssr/render-html-page app-html initial-state)]
        {:status 200
         :headers {"Content-Type" "text/html"}
         :body full-html})
      ;; Pass through for API requests
      (handler request))))

(defn accepts-html? [request]
  (when-let [accept (get-in request [:headers "accept"])]
    (str/includes? accept "text/html")))
```

### Route-Based SSR
```clojure
(defn render-by-route [request]
  (let [uri (:uri request)
        route (parse-uri-to-route uri)]
    (case (:handler route)
      :home (render-home-page)
      :user-profile (render-user-page (:params route))
      :article (render-article-page (:params route))
      ;; Default fallback
      (render-spa-shell))))
```

## SEO Optimization

### Meta Tags and Head Content
```clojure
(defn render-head-tags [page-data]
  (let [{:keys [title description image]} page-data]
    (str
      "<title>" (or title "My App") "</title>"
      "<meta name=\"description\" content=\"" (or description "Default description") "\">"
      "<meta property=\"og:title\" content=\"" title "\">"
      "<meta property=\"og:description\" content=\"" description "\">"
      (when image
        (str "<meta property=\"og:image\" content=\"" image "\">")))))

(defn render-full-page [app-html page-data]
  (str "<!DOCTYPE html>"
       "<html>"
       "<head>"
       (render-head-tags page-data)
       "</head>"
       "<body>"
       "<div id=\"app\">" app-html "</div>"
       "<script>window.PAGE_DATA = " (pr-str page-data) ";</script>"
       "</body>"
       "</html>"))
```

### Structured Data
```clojure
(defn render-structured-data [entity-type entity-data]
  (case entity-type
    :article
    (str "<script type=\"application/ld+json\">"
         (json/encode
           {"@context" "https://schema.org"
            "@type" "Article"
            "headline" (:article/title entity-data)
            "author" {"@type" "Person" "name" (:author/name entity-data)}
            "datePublished" (:article/published-date entity-data)})
         "</script>")
    
    :person
    (str "<script type=\"application/ld+json\">"
         (json/encode
           {"@context" "https://schema.org"
            "@type" "Person"
            "name" (:person/name entity-data)
            "jobTitle" (:person/title entity-data)})
         "</script>")
    
    ""))
```

## Performance Considerations

### Caching Strategies
```clojure
(def page-cache (atom {}))

(defn cached-render [cache-key render-fn]
  (if-let [cached-html (get @page-cache cache-key)]
    cached-html
    (let [html (render-fn)
          ;; Cache for 5 minutes
          expiry (+ (System/currentTimeMillis) (* 5 60 1000))]
      (swap! page-cache assoc cache-key {:html html :expiry expiry})
      html)))

(defn render-with-cache [route-params]
  (let [cache-key (str (:route route-params) "-" (:id route-params))]
    (cached-render cache-key #(render-route route-params))))
```

### Selective SSR
```clojure
(defn should-ssr? [request]
  (and
    ;; Only SSR for crawlers and first-time users
    (or (crawler-user-agent? request)
        (first-visit? request))
    ;; Don't SSR admin pages
    (not (str/starts-with? (:uri request) "/admin"))))

(defn conditional-ssr [handler]
  (fn [request]
    (if (should-ssr? request)
      (render-ssr request)
      (serve-spa-shell request))))
```

## Error Handling

### SSR Error Recovery
```clojure
(defn safe-ssr-render [render-fn fallback-fn]
  (try
    (render-fn)
    (catch Exception e
      (log/error e "SSR rendering failed")
      ;; Fall back to client-side rendering
      (fallback-fn))))

(defn render-page-safely [route-params]
  (safe-ssr-render
    #(render-route route-params)
    #(render-spa-shell))) ; Minimal HTML shell for client rendering
```

### Component Error Boundaries
```clojure
(defsc ErrorBoundary [this props]
  {:componentDidCatch
   (fn [this error info]
     (log/error "Component error during SSR" error info))}
  (dom/div "Error loading content"))

;; Wrap components that might fail
(defsc SafeUserProfile [this props]
  (dom/div
    (ui-error-boundary
      (ui-user-profile props))))
```

## Development Workflow

### Development SSR
```clojure
(defn dev-ssr-middleware [handler]
  (fn [request]
    (if (dev-mode?)
      ;; In development, always render fresh
      (try
        (render-ssr request)
        (catch Exception e
          (log/warn "Dev SSR failed, falling back to SPA" e)
          (serve-spa-shell request)))
      (handler request))))
```

### SSR Testing
```clojure
(deftest ssr-rendering-test
  (testing "renders home page correctly"
    (let [initial-state {:title "Home" :content "Welcome"}
          html (ssr/render-page initial-state)]
      (is (str/includes? html "Welcome"))
      (is (str/includes? html "<h1>Home</h1>"))))
  
  (testing "handles missing data gracefully"
    (let [html (ssr/render-page {})]
      (is (string? html))
      (is (not (str/includes? html "null"))))))
```

## Best Practices

### SSR Guidelines
- **Keep SSR simple**: Don't try to replicate full client functionality
- **Handle failures gracefully**: Always have fallback to client rendering
- **Cache strategically**: Cache expensive operations, not everything
- **Monitor performance**: SSR should improve, not hurt, performance
- **Test thoroughly**: Ensure SSR and client rendering produce same results

### State Management
- **Minimal initial state**: Only include data needed for initial render
- **Normalize data**: Use same database format as client
- **Avoid side effects**: No component lifecycle methods during SSR
- **Handle async carefully**: Complete all data loading before rendering

### SEO Optimization
- **Unique titles and descriptions**: Each page should have distinct meta tags
- **Structured data**: Include JSON-LD for rich search results
- **Clean URLs**: Use semantic, crawler-friendly URLs
- **Fast loading**: SSR should reduce, not increase, time to first byte