
# Server-Side Rendering

## Overview

Fulcro supports server-side rendering (SSR) for improved SEO, faster initial page loads, and better user experience on slow connections. Unlike many frameworks, Fulcro's architecture makes SSR surprisingly simple because you can write your entire application in CLJC, sharing code between client and server.

## Core Concepts

### Benefits of SSR

- **SEO improvement**: Search engines can crawl rendered content
- **Faster perceived performance**: Users see content immediately without waiting for JavaScript to load
- **Better accessibility**: Screen readers can access initial content
- **Graceful degradation**: Content is available even if JavaScript fails to load

### Isomorphic Architecture

Fulcro components are written in CLJC, enabling:

- **Shared code**: Same components and logic run client and server
- **Consistent rendering**: Identical HTML output on both sides
- **Unified state management**: Same normalized database format on client and server

## Overall SSR Architecture

Here's how Fulcro's SSR works:

1. Write your entire client application in CLJC files
2. Use conditional reader syntax to select server DOM functions for CLJ and client DOM for CLJS
3. On the server: Build a normalized application database from initial state and mutations
4. On the server: Render the database to an HTML string
5. On the server: Embed the normalized database as transit-encoded EDN in a `<script>` tag on `js/window`
6. On the client: Retrieve the server-generated state from `js/window`
7. On the client: Mount React with `:hydrate? true` to attach to existing DOM

## CLJC File Setup

To support SSR, use conditional reader syntax in your UI files:

```clojure
(ns app.ui
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

**Important**: Use `com.fulcrologic.fulcro.dom-server` only on the JVM. This module provides server-side DOM rendering functions. CLJS will use the normal `com.fulcrologic.fulcro.dom` module.

## Building Server-Side Application State

The core SSR workflow happens in the `com.fulcrologic.fulcro.algorithms.server-render` namespace. There are two approaches to building application state on the server:

### State Building (Recommended)

Use `build-initial-state` to create a normalized client database, then evolve it using mutation implementations. This is the preferred approach because it's free from timing issues.

```clojure
(ns app.server
  (:require
    [com.fulcrologic.fulcro.algorithms.server-render :as ssr]
    [com.fulcrologic.fulcro.components :as comp]
    [com.fulcrologic.fulcro.algorithms.denormalize :as fdn]
    [app.ui :as ui]))

(defn build-initial-state-for-route [request]
  (let [base-state (ssr/build-initial-state (comp/get-initial-state ui/Root {}) ui/Root)
        user-data (load-user-from-session request)
        user-ident (comp/get-ident ui/User user-data)]
    ;; Evolve the state by applying mutation logic
    (-> base-state
      (assoc :current-user user-ident)
      (assoc-in user-ident user-data))))
```

**What `build-initial-state` does:**
- Takes the root component and an initial state tree
- Normalizes the state according to component idents
- Plugs in any union branch data not explicitly in the tree
- Returns a normalized client application database ready for rendering

### Headless App Approach (Not Recommended)

You could run the application in headless mode with loopback remotes, trigger mutations, and wait for queues to settle. This approach is currently discouraged because it lacks proper testing and supporting hooks. The State Building approach is preferred.

## Rendering to HTML

Once you have the normalized database, rendering to an HTML string is straightforward:

```clojure
(defn render-app-html [app normalized-db]
  (let [props (fdn/db->tree (comp/get-query ui/Root normalized-db) normalized-db normalized-db)
        root-factory (comp/factory ui/Root)]
    ;; CRITICAL: Bind the app in context (some Fulcro APIs require this)
    (binding [comp/*app* app]
      (dom/render-to-str (root-factory props)))))
```

**Key points:**
- `db->tree` converts normalized database state to props for component rendering
- The third argument to `db->tree` is the state map used for normalization lookups
- `dom/render-to-str` (from `dom-server`) converts a component to an HTML string
- The `comp/*app*` binding is required for some Fulcro APIs to function during rendering
- Your renderer is pure: it will render exactly what the state says the application is in

## State Hydration

The server must send the complete normalized database to the client so React can hydrate without flickering (React requires the initial DOM to match exactly).

### Server-Side: Encoding the State

Use `initial-state->script-tag` to create a script tag with the transit-encoded state:

```clojure
(defn render-html-page [app normalized-db]
  (let [props (fdn/db->tree (comp/get-query ui/Root normalized-db) normalized-db normalized-db)
        root-factory (comp/factory ui/Root)
        app-html (binding [comp/*app* app]
                   (dom/render-to-str (root-factory props)))
        initial-state-script (ssr/initial-state->script-tag normalized-db)]
    (str "<!DOCTYPE html>\n"
      "<html lang='en'>\n"
      "<head>\n"
      "<meta charset='UTF-8'>\n"
      "<meta name='viewport' content='width=device-width, initial-scale=1'>\n"
      "<title>My App</title>\n"
      initial-state-script
      "</head>\n"
      "<body>\n"
      "<div id='app'>" app-html "</div>\n"
      "<script src='js/app.js' type='text/javascript'></script>\n"
      "</body>\n"
      "</html>\n")))
```

**Why this matters:** The `initial-state->script-tag` function handles proper transit encoding of the EDN data structure. Do not try to manually encode state with `pr-str` or `json/encode` - use this function.

### Client-Side: Hydrating from Server State

On the client, retrieve the server-generated state and mount with hydration enabled:

```clojure
(ns app.client
  (:require
    [com.fulcrologic.fulcro.application :as app]
    [com.fulcrologic.fulcro.algorithms.server-render :as ssr]
    [app.ui :as ui]))

(defonce fulcro-app (app/fulcro-app {}))

(defn ^:export start-from-ssr []
  "Start the application after server-side rendering"
  (let [db (ssr/get-SSR-initial-state)]
    ;; Replace default client state with server-generated state
    (reset! (::app/state-atom fulcro-app) db)
    ;; Mount with hydration to preserve server-rendered DOM
    (app/mount! fulcro-app ui/Root "app" 
      {:hydrate? true :initialize-state? false})))
```

**Critical options:**
- `:hydrate? true` tells React to attach to existing DOM instead of creating new elements
- `:initialize-state? false` prevents Fulcro from calling `get-initial-state` (we already have the state from the server)

## Dynamic Routes with SSR

To render different content based on the request URL, install routes in the server-built database:

```clojure
(ns app.server
  (:require
    [com.fulcrologic.fulcro.algorithms.server-render :as ssr]
    [com.fulcrologic.fulcro.routing.legacy-ui-routers :as r]
    [com.fulcrologic.fulcro.components :as comp]
    [com.fulcrologic.fulcro.algorithms.denormalize :as fdn]))

(defn build-ui-tree-for-route [request route-handler]
  (let [;; Start with clean normalized state
        client-db (ssr/build-initial-state (comp/get-initial-state ui/Root {}) ui/Root)
        ;; Install all route targets
        db-with-routes (-> client-db
                         (r/install-route* :home ui/HomePage)
                         (r/install-route* :profile ui/ProfilePage))
        ;; Navigate to the requested route
        final-db (r/route-to* db-with-routes route-handler)]
    ;; CRITICAL: Pass final-db to get-query so dynamic route queries are correct
    (fdn/db->tree (comp/get-query ui/Root final-db) final-db final-db)))
```

**Important:** Pass the final database to `get-query` after routing. Dynamic routes have queries that change based on the current route, so `get-query` needs to see the updated database state.

## Performance Considerations

### Caching Strategy

Cache expensive SSR renders to avoid repeated computation:

```clojure
(def page-cache (atom {}))

(defn cached-render [cache-key render-fn]
  (if-let [cached (get @page-cache cache-key)]
    cached
    (let [html (render-fn)]
      (swap! page-cache assoc cache-key html)
      html)))

(defn render-request [request]
  (let [cache-key (str (:uri request))]
    (cached-render cache-key #(render-page-for-request request))))
```

### Selective SSR

Only perform SSR for certain requests (search engine crawlers, first visits) and fall back to client-side rendering for others:

```clojure
(defn should-ssr? [request]
  (and
    (or (crawler-user-agent? (:headers request))
        (first-time-visitor? request))
    (not (admin-page? (:uri request)))))

(defn handle-request [request]
  (if (should-ssr? request)
    (render-ssr request)
    (serve-spa-shell request)))
```

## Error Handling

### Graceful Fallback

If SSR rendering fails, fall back to serving a client-side rendering shell:

```clojure
(defn safe-ssr-render [request]
  (try
    (render-page-with-ssr request)
    (catch Exception e
      (log/error e "SSR rendering failed, falling back to SPA")
      ;; Serve minimal HTML that lets client take over
      (serve-spa-shell request))))
```

### Development SSR

During development, always render fresh SSR output and handle errors gracefully:

```clojure
(defn dev-ssr-handler [request]
  (if (dev-mode?)
    (try
      (render-ssr request)
      (catch Exception e
        (log/warn "Dev SSR failed, falling back to SPA" e)
        (serve-spa-shell request)))
    (render-ssr request)))
```

## Testing SSR

Test that SSR rendering produces valid HTML and matches client rendering:

```clojure
(deftest ssr-rendering-test
  (testing "renders home page correctly"
    (let [state {:title "Home" :content "Welcome"}
          html (render-app-html state)]
      (is (string? html))
      (is (str/includes? html "Welcome"))
      (is (str/includes? html "<h1>Home</h1>"))))
  
  (testing "handles missing data gracefully"
    (let [html (render-app-html {})]
      (is (string? html))
      (is (not (str/includes? html "null"))))))
```

## Best Practices

### General SSR Guidelines

- **Keep rendering pure**: Don't perform side effects during rendering
- **Build state completely**: Fetch all required data before calling `render-to-str`
- **Avoid React lifecycle**: Component lifecycle methods (like `componentDidMount`) don't run during SSR
- **Handle missing data**: Provide sensible defaults or conditional rendering for data that might not be available
- **Cache strategically**: Cache rendered pages, not individual components
- **Monitor performance**: SSR adds server overhead; measure to ensure it improves real metrics

### State Management

- **Normalize data**: Use the same normalized database format as the client
- **Provide initial state**: Components should have `get-initial-state` implementations
- **Avoid mutations in render**: Only evolve state before rendering, not during it

### SEO Optimization

- **Unique titles and descriptions**: Use your components' props to set distinct page meta information
- **Structured data**: Include JSON-LD schema markup for rich search results
- **Semantic URLs**: Use descriptive, crawler-friendly URL patterns
- **Fast rendering**: Monitor time-to-first-byte; SSR should reduce it, not increase it

## Code Splitting with SSR

<!-- TODO: Verify this claim -->
Note: The Fulcro documentation for Code Splitting + SSR was not updated for Fulcro 3 and may not work as described. If you're using dynamic routing with code splitting, test thoroughly to ensure SSR works correctly with your route loading strategy.

Dynamic routers (via `com.fulcrologic.fulcro.routing.legacy-ui-routers`) can be used with SSR by installing routes and navigating to the correct route before rendering. Code splitting (via `cljs.loader`) may require additional configuration to work correctly with server-side rendering.

## Limitations and Caveats

- **Server-side rendering on the server only**: The `dom-server` module is JVM-only and cannot be used in CLJS
- **No client lifecycle during SSR**: React lifecycle methods don't execute on the server
- **Exact DOM matching**: The server-rendered HTML must match exactly what React will render on the client, or hydration will fail with warnings
- **Stateful components**: Avoid stateful components (which rely on React state) in your CLJC code, or provide server-friendly implementations
- **Performance cost**: SSR adds computation on every request; use caching to offset this
