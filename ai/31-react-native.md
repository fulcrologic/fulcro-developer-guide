
# React Native Integration

## Overview

Fulcro works excellently with React Native for building mobile applications. The framework's data-driven architecture and normalized database approach provide significant advantages for mobile development, offering excellent performance and code reuse opportunities.

## Getting Started with Fulcro Native

### Recommended Setup

**Use Expo**: Currently recommended approach for React Native development with Fulcro
**Fulcro Native Library**: Provides essential setup utilities and helpers

```clojure
;; Add to deps.edn
{:deps {com.fulcrologic/fulcro {:mvn/version "3.7.0"}}}
```

<!-- TODO: Verify fulcro-native library existence and version -->

### Template Project

Start with building from examples. The Fulcro ecosystem provides resources for React Native integration:

```bash
# Refer to Fulcro documentation for current template recommendations
# React Native setup via Expo:
npx create-expo-app your-app-name
```

### Basic Setup Steps

1. **Configure package.json** with React Native dependencies
2. **Set up Shadow-cljs build** targeting React Native (separate from web build)
3. **Wrap native UI components** for Fulcro component integration
4. **Initialize Fulcro app** with React Native renderer

## Core Integration Requirements

### Package.json Dependencies

```json
{
  "dependencies": {
    "react": "^18.2.0",
    "react-native": "^0.73.0",
    "expo": "^51.0.0",
    "@react-native-async-storage/async-storage": "^1.23.0"
  }
}
```

### Fulcro App Configuration

```clojure
(ns mobile-app.core
  (:require
    [com.fulcrologic.fulcro.application :as app]
    [com.fulcrologic.fulcro.components :as comp]
    [com.fulcrologic.fulcro.react.error-boundaries :as eb]
    ["react-native" :as rn]))

;; Configure for React Native
(def mobile-app
  (app/fulcro-app
    {:optimistic? true
     :root-class Root}))
```

<!-- TODO: Verify render-middleware approach for React Native mounting -->

### Native Component Wrappers

React Native components need to be wrapped for use with Fulcro's component system. Use `createElement` directly from React Native:

```clojure
(ns mobile-app.ui.native
  (:require
    [com.fulcrologic.fulcro.components :as comp]
    ["react-native" :as rn]))

;; Create factory functions for React Native components
(defn ui-view [props & children]
  (apply rn/createElement rn/View (clj->js props) children))

(defn ui-text [props & children]
  (apply rn/createElement rn/Text (clj->js props) children))

(defn ui-touchable [props & children]
  (apply rn/createElement rn/TouchableOpacity (clj->js props) children))

(defn ui-text-input [props & children]
  (apply rn/createElement rn/TextInput (clj->js props) children))

(defn ui-scroll-view [props & children]
  (apply rn/createElement rn/ScrollView (clj->js props) children))

(defn ui-flat-list [props & children]
  (apply rn/createElement rn/FlatList (clj->js props) children))
```

<!-- NOTE: `dom/native-element` is not a standard Fulcro API. Use direct `createElement` as shown above. -->

## Source Code Sharing

### The Challenge

Sharing code between web and mobile platforms requires careful management of platform-specific dependencies, particularly DOM-related requires that can break mobile builds.

### Finding Dependency Leaks

**Shadow-cljs Web Interface**:
1. Navigate to `Builds -> your-build-name`
2. Scroll to dependency analysis tool
3. Search for problematic libraries (often prefixed with `shadow.js.shim.module$`)
4. Trace the require chain causing the leak

### Protocol-Based Platform Abstraction

The recommended pattern for sharing code between web and mobile is using Clojure protocols to abstract platform-specific implementations:

#### Step 1: Runtime Environment

```clojure
(ns app.targets.runtime)

(defonce runtime (atom {}))
```

#### Step 2: Platform-Specific Target Files

**Browser Target:**
```clojure
(ns app.targets.browser
  (:require
    [app.api.auth-browser :as auth]
    [app.api.routing-browser :as routing]
    [app.api.storage-browser :as storage]))

(defn init! []
  (auth/init!)
  (routing/init!)
  (storage/init!))
```

**Native Target:**
```clojure
(ns app.targets.native
  (:require
    [app.api.auth-native :as auth]
    [app.api.routing-native :as routing]
    [app.api.storage-native :as storage]))

(defn init! []
  (auth/init!)
  (routing/init!)
  (storage/init!))
```

#### Step 3: Generic API Abstraction

```clojure
(ns app.api.storage
  (:require [app.targets.runtime :refer [runtime]]))

(defprotocol Storage
  (-save-item! [impl key value] "Save item to platform storage")
  (-get-item [impl key] "Retrieve item from platform storage")
  (-remove-item! [impl key] "Remove item from platform storage"))

(defn save-item! [key value]
  (when-let [impl (get @runtime ::storage-impl)]
    (-save-item! impl key value)))

(defn get-item [key]
  (when-let [impl (get @runtime ::storage-impl)]
    (-get-item impl key)))

(defn remove-item! [key]
  (when-let [impl (get @runtime ::storage-impl)]
    (-remove-item! impl key)))
```

#### Step 4: Browser Implementation

```clojure
(ns app.api.storage-browser
  (:require
    [app.api.storage :as storage]
    [app.targets.runtime :refer [runtime]]
    [com.fulcrologic.fulcro.algorithms.transit :as transit]))

(defn set-local-storage! [key value]
  (.setItem (.-localStorage js/window) 
            (str key) 
            (transit/transit-clj->str value)))

(defn get-local-storage [key]
  (when-let [result (.getItem (.-localStorage js/window) (str key))]
    (transit/transit-str->clj result)))

(defn remove-local-storage! [key]
  (.removeItem (.-localStorage js/window) (str key)))

(defn init! []
  (swap! runtime assoc ::storage/storage-impl
         (reify storage/Storage
           (-save-item! [_ key value]
             (set-local-storage! key value))
           (-get-item [_ key]
             (get-local-storage key))
           (-remove-item! [_ key]
             (remove-local-storage! key)))))
```

#### Step 5: Native Implementation

```clojure
(ns app.api.storage-native
  (:require
    [app.api.storage :as storage]
    [app.targets.runtime :refer [runtime]]
    [com.fulcrologic.fulcro.algorithms.transit :as transit]
    ["@react-native-async-storage/async-storage" :default AsyncStorage]))

(defn save-async-storage! [key value]
  (.setItem AsyncStorage 
            (str key) 
            (transit/transit-clj->str value)))

(defn get-async-storage [key]
  (-> (.getItem AsyncStorage (str key))
      (.then #(when % (transit/transit-str->clj %)))))

(defn remove-async-storage! [key]
  (.removeItem AsyncStorage (str key)))

(defn init! []
  (swap! runtime assoc ::storage/storage-impl
         (reify storage/Storage
           (-save-item! [_ key value]
             (save-async-storage! key value))
           (-get-item [_ key]
             (get-async-storage key))
           (-remove-item! [_ key]
             (remove-async-storage! key)))))
```

#### Step 6: Entry Point Configuration

**Browser Entry:**
```clojure
(ns app.browser.main
  (:require
    [app.targets.browser :as browser]
    [app.core :as core]
    [com.fulcrologic.fulcro.application :as app]))

(defn init []
  (browser/init!)  ; Initialize browser implementations
  (app/mount! core/mobile-app core/Root "app"))
```

**Native Entry:**
```clojure
(ns app.native.main
  (:require
    [app.targets.native :as native]
    [app.core :as core]
    [com.fulcrologic.fulcro.application :as app]
    ["expo" :as Expo]))

(defn init []
  (native/init!)  ; Initialize native implementations
  (app/mount! core/mobile-app core/Root "app"))

;; Register with Expo
(.registerRootComponent Expo (fn [] (init)))
```

## Complete Mobile Application Example

### App Structure

```clojure
(ns mobile-app.ui.root
  (:require
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    [com.fulcrologic.fulcro.routing.dynamic-routing :as dr]
    [mobile-app.ui.native :as n]
    [mobile-app.ui.screens.home :as home]
    [mobile-app.ui.screens.profile :as profile]))

(dr/defrouter MainRouter [this {:keys [current-route route-props]}]
  {:router-targets {:home home/HomeScreen
                    :profile profile/ProfileScreen}})

(defsc Root [this {:keys [main-router]}]
  {:query [{:main-router (comp/get-query MainRouter)}]
   :initial-state {:main-router {}}}
  (n/ui-view
    {:style {:flex 1 :backgroundColor "#fff"}}
    ((comp/factory MainRouter) main-router)))
```

### Home Screen

```clojure
(ns mobile-app.ui.screens.home
  (:require
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    [com.fulcrologic.fulcro.routing.dynamic-routing :as dr]
    [mobile-app.ui.native :as n]))

(defsc HomeScreen [this {:keys [home/title home/message]}]
  {:query [:home/title :home/message]
   :ident (fn [] [:screen/id :home])
   :initial-state {:home/title "Welcome!"
                  :home/message "This is a Fulcro Native app"}
   :route-segment ["home"]}
  (n/ui-view
    {:style {:flex 1 :justifyContent "center" :alignItems "center" :padding 20}}
    (n/ui-text
      {:style {:fontSize 24 :fontWeight "bold" :marginBottom 20}}
      title)
    (n/ui-text
      {:style {:fontSize 16 :textAlign "center" :marginBottom 30}}
      message)
    (n/ui-touchable
      {:style {:backgroundColor "#007AFF" :padding 15 :borderRadius 8}
       :onPress #(dr/change-route! this ["profile"])}
      (n/ui-text
        {:style {:color "white" :fontWeight "bold"}}
        "Go to Profile"))))
```

### Profile Screen with Data Loading

```clojure
(ns mobile-app.ui.screens.profile
  (:require
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    [com.fulcrologic.fulcro.data-fetch :as df]
    [com.fulcrologic.fulcro.mutations :as m]
    [mobile-app.ui.native :as n]))

(m/defmutation logout [_]
  (action [{:keys [state]}]
    (swap! state dissoc :user/name :user/email)))

(defsc ProfileScreen [this {:keys [user/name user/email ui/loading?] :as props}]
  {:query [:user/name :user/email :ui/loading?]
   :ident (fn [] [:screen/id :profile])
   :initial-state {:ui/loading? false}
   :route-segment ["profile"]
   :will-enter (fn [app route-params]
                 (df/load! app :current-user nil)
                 (dr/route-immediate [:screen/id :profile]))}
  (n/ui-view
    {:style {:flex 1 :padding 20}}
    (n/ui-text
      {:style {:fontSize 20 :fontWeight "bold" :marginBottom 20}}
      "Profile")
    (if loading?
      (n/ui-text "Loading...")
      (n/ui-view
        (n/ui-text
          {:style {:fontSize 16 :marginBottom 10}}
          (str "Name: " (or name "Unknown")))
        (n/ui-text
          {:style {:fontSize 16 :marginBottom 20}}
          (str "Email: " (or email "No email")))
        (n/ui-touchable
          {:style {:backgroundColor "#FF3B30" :padding 15 :borderRadius 8}
           :onPress #(comp/transact! this [(logout {})])}
          (n/ui-text
            {:style {:color "white" :fontWeight "bold"}}
            "Logout"))))))
```

<!-- TODO: Verify that `df/load!` with nil for query-class is valid or if component class must be provided -->

## Platform-Specific Styling

### Style Abstraction

```clojure
(ns mobile-app.ui.styles
  (:require ["react-native" :as rn]))

(def styles
  {:container {:flex 1
               :backgroundColor "#ffffff"
               :padding 20}
   :title {:fontSize 24
           :fontWeight "bold"
           :color "#333333"
           :marginBottom 20}
   :button {:backgroundColor "#007AFF"
            :padding 15
            :borderRadius 8
            :alignItems "center"}
   :button-text {:color "white"
                 :fontWeight "bold"
                 :fontSize 16}})

(defn get-style [key]
  (get styles key {}))

;; Platform-specific styles
(def platform-styles
  (if (= (.-Platform.OS rn/Platform) "ios")
    {:shadow {:shadowColor "#000"
              :shadowOffset {:width 0 :height 2}
              :shadowOpacity 0.1
              :shadowRadius 4}}
    {:elevation {:elevation 4}}))
```

## Best Practices

### 1. Component Organization

```
src/
  shared/           ; Shared business logic
    mutations/
    api/
  mobile/          ; Mobile-specific UI
    ui/
      native/      ; Native component wrappers
      screens/     ; Screen components
      styles/      ; Style definitions
  browser/         ; Web-specific UI (if applicable)
    ui/
```

### 2. Navigation Integration

Fulcro's dynamic routing integrates seamlessly with React Native:

```clojure
;; Navigate to a route
(dr/change-route! this ["home"])

;; Navigate with parameters
(dr/change-route! this ["profile" user-id])

;; Use will-enter for deferred/async navigation
(defsc ProfileScreen [this props]
  {...
   :will-enter (fn [app {:keys [user-id]}]
                 (df/load! app [:user/id user-id] User)
                 (dr/route-deferred [:screen/id :profile]
                   #(df/load! app [:user/id user-id] User
                      {:post-mutation `dr/target-ready
                       :post-mutation-params {:target [:screen/id :profile]}})))}
  ...)
```

### 3. State Management

Leverage Fulcro's normalized database for effective state management:

```clojure
(ns mobile-app.mutations.cache
  (:require
    [com.fulcrologic.fulcro.mutations :as m]
    [com.fulcrologic.fulcro.algorithms.merge :as merge]))

(m/defmutation cache-user-data [user-data]
  (action [{:keys [state]}]
    (swap! state merge/merge-component User user-data)
    ;; Persist to native storage
    (storage/save-item! :cached-user user-data)))
```

### 4. Performance Optimization

React Native has specific performance considerations:

```clojure
(defsc OptimizedList [this {:keys [items]}]
  {:shouldComponentUpdate (fn [next-props next-state]
                            ;; Custom optimization logic
                            (not= (:items next-props) items))}
  (n/ui-flat-list
    {:data items
     :keyExtractor #(str (:id %))
     :renderItem (fn [item] ((comp/factory ItemRow {:keyfn :id}) item))
     :removeClippedSubviews true
     :maxToRenderPerBatch 10}))
```

<!-- TODO: Verify shouldComponentUpdate is still the recommended approach vs newer React lifecycle methods -->

### 5. Testing Strategy

Test business logic independently of the UI:

```clojure
(ns mobile-app.mutations-test
  (:require
    [cljs.test :refer [deftest is]]
    [com.fulcrologic.fulcro.algorithms.merge :as merge]
    [mobile-app.models.user :refer [User]]))

(deftest user-profile-test
  (let [test-user {:user/id 1 :user/name "Test User" :user/email "test@example.com"}
        state-map (merge/merge-component {} User test-user)]
    (is (= "Test User" (get-in state-map [:user/id 1 :user/name])))))
```

## Common Patterns

### Offline Data Synchronization

Fulcro's normalized database supports offline-first patterns:

```clojure
(ns mobile-app.mutations.sync
  (:require
    [com.fulcrologic.fulcro.mutations :as m]
    [com.fulcrologic.fulcro.components :as comp]))

(m/defmutation sync-offline-data [_]
  (action [{:keys [state]}]
    (let [offline-mutations (get @state :offline/pending-mutations [])]
      (doseq [mutation offline-mutations]
        (comp/transact! this [mutation]))
      (swap! state dissoc :offline/pending-mutations))))
```

### Push Notifications

Integrate Expo notifications for mobile alerts:

```clojure
(ns mobile-app.notifications
  (:require ["expo-notifications" :as Notifications]))

(defn setup-notifications! []
  (-> (Notifications/requestPermissionsAsync)
      (.then #(when (get-in % [:permissions :granted])
                (register-notification-handlers!)))))

(defn register-notification-handlers! []
  (Notifications/addNotificationReceivedListener
    (fn [notification]
      ;; Handle notification received while app is open
      (js/console.log notification))))
```

### File Management

Access the file system for persisting application data:

```clojure
(ns mobile-app.files
  (:require ["expo-file-system" :as FileSystem]))

(defn save-file! [filename data]
  (let [file-uri (str (.-documentDirectory FileSystem) filename)]
    (FileSystem/writeAsStringAsync file-uri data)))

(defn read-file! [filename]
  (let [file-uri (str (.-documentDirectory FileSystem) filename)]
    (FileSystem/readAsStringAsync file-uri)))
```

## Summary

Fulcro's React Native integration provides a robust foundation for building sophisticated mobile applications while maintaining excellent code reuse with web applications. The protocol-based abstraction pattern allows you to share your core business logic, mutations, and state management across web and mobile platforms, while keeping platform-specific UI implementations cleanly separated.
