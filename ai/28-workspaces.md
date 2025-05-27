# Development Workspaces

## Overview

Workspaces provide an interactive development environment for Fulcro applications, allowing developers to build, test, and iterate on components in isolation. This tool is particularly valuable for component-driven development and design system creation.

## What are Workspaces?

Workspaces is a development tool that creates an interactive dashboard where you can:

- **Develop Components in Isolation**: Work on individual components without running the entire application
- **Test Different States**: Easily test components with various data configurations
- **Create Living Documentation**: Build interactive examples that serve as documentation
- **Design System Development**: Perfect for building and testing reusable UI components

## Getting Started

### Template Integration

The [Fulcro template](https://github.com/fulcrologic/fulcro-template) includes Workspaces pre-configured:

```bash
npx create-fulcro-app my-app
cd my-app
npm install
npm run workspaces
```

### Manual Setup

Add Workspaces to an existing project:

```clojure
;; deps.edn
{:aliases
 {:workspaces
  {:extra-deps {nubank/workspaces {:mvn/version "1.1.2"}}
   :main-opts ["-m" "shadow.cljs.devtools.cli" "watch" "workspaces"]}}}
```

```clojure
;; shadow-cljs.edn
{:builds
 {:workspaces
  {:target :browser
   :output-dir "resources/public/workspaces/js"
   :asset-path "/workspaces/js"
   :module-loader true
   :modules {:main {:init-fn your-app.workspaces.main/init}}
   :devtools {:http-root "resources/public/workspaces"
             :http-port 8023}}}}
```

## Types of Workspace Cards

### 1. Plain React Components

Test pure React components without Fulcro infrastructure:

```clojure
(ns my-app.workspaces.components
  (:require
    [nubank.workspaces.core :as ws]
    [nubank.workspaces.card-types.react :as ct.react]
    [my-app.ui.button :as button]))

(ws/defcard plain-button-card
  (ct.react/react-card
    (button/ui-button {:label "Click me"
                      :onClick #(js/alert "Clicked!")})))
```

### 2. Fulcro Component Display

Display Fulcro components with sample data:

```clojure
(ns my-app.workspaces.user-components
  (:require
    [nubank.workspaces.core :as ws]
    [nubank.workspaces.card-types.fulcro3 :as ct.fulcro]
    [my-app.ui.user :as user]))

(ws/defcard user-profile-card
  (ct.fulcro/fulcro-card
    {::ct.fulcro/root user/UserProfile
     ::ct.fulcro/initial-state 
     {:user/id 1
      :user/name "Alice Johnson"
      :user/email "alice@example.com"
      :user/avatar "https://example.com/avatar.jpg"
      :user/role :admin}}))
```

### 3. Live Application Segments

Create fully interactive application segments:

```clojure
(ns my-app.workspaces.forms
  (:require
    [nubank.workspaces.core :as ws]
    [nubank.workspaces.card-types.fulcro3 :as ct.fulcro]
    [com.fulcrologic.fulcro.components :as comp]
    [my-app.ui.forms.user-form :as user-form]))

(ws/defcard live-user-form
  (ct.fulcro/fulcro-card
    {::ct.fulcro/root user-form/UserForm
     ::ct.fulcro/initial-state (comp/get-initial-state user-form/UserForm {})
     ::ct.fulcro/app (app/fulcro-app {})}))  ; Full Fulcro app instance
```

## Advanced Workspace Patterns

### Component Variations

Test multiple states of the same component:

```clojure
(ws/defcard button-variations
  (ct.react/react-card
    (dom/div
      (dom/h3 "Button States")
      (dom/div {:style {:display "flex" :gap "10px" :margin "10px 0"}}
        (button/ui-button {:label "Normal" :variant :primary})
        (button/ui-button {:label "Loading" :variant :primary :loading? true})
        (button/ui-button {:label "Disabled" :variant :primary :disabled? true}))
      (dom/div {:style {:display "flex" :gap "10px" :margin "10px 0"}}
        (button/ui-button {:label "Secondary" :variant :secondary})
        (button/ui-button {:label "Danger" :variant :danger})
        (button/ui-button {:label "Link" :variant :link})))))
```

### Data-Driven Cards

Generate cards programmatically:

```clojure
(def user-data-variations
  [{:user/name "John Doe" :user/role :user :user/status :active}
   {:user/name "Jane Admin" :user/role :admin :user/status :active}
   {:user/name "Bob Inactive" :user/role :user :user/status :inactive}])

(doseq [[index user-data] (map-indexed vector user-data-variations)]
  (ws/defcard (keyword (str "user-card-" index))
    (ct.fulcro/fulcro-card
      {::ct.fulcro/root user/UserCard
       ::ct.fulcro/initial-state user-data})))
```

### Interactive Development

Create cards with controls for testing:

```clojure
(ws/defcard interactive-form-card
  {::ws/card-width 2  ; Take up more space
   ::ws/card-height 12}
  (ct.fulcro/fulcro-card
    {::ct.fulcro/root form/InteractiveForm
     ::ct.fulcro/initial-state 
     {:form/fields {:name "" :email "" :role :user}
      :form/validation {}
      :form/submission-status :ready}}))
```

## Workspace Organization

### Namespace Structure

```clojure
src/
  workspaces/
    main.cljs           ; Entry point
    components/         ; Component demonstrations
      buttons.cljs
      forms.cljs
      navigation.cljs
    pages/             ; Full page demonstrations
      dashboard.cljs
      user_management.cljs
    design_system/     ; Design system components
      colors.cljs
      typography.cljs
      spacing.cljs
```

### Main Entry Point

```clojure
(ns my-app.workspaces.main
  (:require
    [nubank.workspaces.core :as ws]
    ;; Import all workspace namespaces
    [my-app.workspaces.components.buttons]
    [my-app.workspaces.components.forms]
    [my-app.workspaces.pages.dashboard]))

(defonce init-workspaces
  (ws/mount-workspace
    (.getElementById js/document "app")
    {:selected-card-id ::my-app.workspaces.components.buttons/primary-button}))

(defn ^:export init []
  init-workspaces)

(defn ^:dev/after-load refresh []
  init-workspaces)
```

## Design System Development

### Color Palette Card

```clojure
(ws/defcard color-palette
  (ct.react/react-card
    (let [colors {:primary "#007AFF"
                 :secondary "#5856D6" 
                 :success "#34C759"
                 :warning "#FF9500"
                 :danger "#FF3B30"
                 :gray-50 "#F9FAFB"
                 :gray-900 "#111827"}]
      (dom/div
        (dom/h3 "Color Palette")
        (for [[name color] colors]
          (dom/div {:key name :style {:display "flex" :align-items "center" :margin "8px 0"}}
            (dom/div {:style {:width "50px" :height "50px" :background-color color 
                             :border "1px solid #ddd" :margin-right "16px"}})
            (dom/div
              (dom/strong (str name))
              (dom/div {:style {:font-family "monospace" :color "#666"}} color))))))))
```

### Typography Scale

```clojure
(ws/defcard typography-scale
  (ct.react/react-card
    (let [type-scale [{:name "Heading 1" :size "2rem" :weight "bold"}
                     {:name "Heading 2" :size "1.5rem" :weight "bold"}
                     {:name "Heading 3" :size "1.25rem" :weight "semibold"}
                     {:name "Body" :size "1rem" :weight "normal"}
                     {:name "Caption" :size "0.875rem" :weight "normal"}]]
      (dom/div
        (dom/h3 "Typography Scale")
        (for [{:keys [name size weight]} type-scale]
          (dom/div {:key name :style {:margin "16px 0"}}
            (dom/div {:style {:font-size size :font-weight weight}}
              (str name " - " size))
            (dom/div {:style {:font-size "0.75rem" :color "#666" :font-family "monospace"}}
              (str "font-size: " size ", font-weight: " weight))))))))
```

## Testing Scenarios

### Form Validation States

```clojure
(ws/defcard form-validation-states
  (ct.react/react-card
    (dom/div
      (dom/h3 "Form Validation States")
      
      ;; Valid state
      (dom/div {:style {:margin "20px 0"}}
        (dom/h4 "Valid State")
        (form-field/ui-text-input 
          {:value "john@example.com"
           :validation-state :valid
           :helper-text "Email looks good!"}))
      
      ;; Error state  
      (dom/div {:style {:margin "20px 0"}}
        (dom/h4 "Error State")
        (form-field/ui-text-input
          {:value "invalid-email"
           :validation-state :error
           :error-message "Please enter a valid email address"}))
      
      ;; Loading state
      (dom/div {:style {:margin "20px 0"}}
        (dom/h4 "Loading State")
        (form-field/ui-text-input
          {:value "checking@example.com"
           :validation-state :loading
           :helper-text "Checking availability..."})))))
```

### Responsive Breakpoints

```clojure
(ws/defcard responsive-component
  {::ws/card-width 4}  ; Full width card
  (ct.react/react-card
    (dom/div
      (dom/h3 "Responsive Component Testing")
      (for [width ["320px" "768px" "1024px" "1440px"]]
        (dom/div {:key width :style {:margin "20px 0"}}
          (dom/h4 (str "Width: " width))
          (dom/div {:style {:width width :border "1px solid #ddd" :padding "16px"}}
            (responsive-card/ui-responsive-card 
              {:title "Sample Card"
               :content "This card adapts to different screen sizes."})))))))
```

## Development Workflow

### 1. Component-First Development

Start with workspace cards before integrating into the main app:

```clojure
;; 1. Create component in workspace
(ws/defcard new-feature-card
  (ct.fulcro/fulcro-card
    {::ct.fulcro/root new-feature/NewFeature
     ::ct.fulcro/initial-state sample-data}))

;; 2. Iterate and refine
;; 3. Test various states
;; 4. Integrate into main application
```

### 2. Bug Reproduction

Create cards that reproduce specific bugs:

```clojure
(ws/defcard bug-reproduction-card
  "Reproduces issue #123: Form doesn't validate on blur"
  (ct.fulcro/fulcro-card
    {::ct.fulcro/root problematic-form/Form
     ::ct.fulcro/initial-state bug-state-data}))
```

### 3. Performance Testing

Test components with large datasets:

```clojure
(ws/defcard performance-test-large-list
  (ct.fulcro/fulcro-card
    {::ct.fulcro/root list-component/VirtualizedList
     ::ct.fulcro/initial-state 
     {:items (vec (for [i (range 10000)]
                   {:id i :name (str "Item " i)}))}}))
```

## Best Practices

### 1. Organize by Feature

Group related components and their variations together.

### 2. Include Edge Cases

Test empty states, error states, and loading states.

### 3. Document Behavior

Use card descriptions to explain component behavior and usage.

### 4. Keep Cards Simple

Each card should focus on one aspect or state of a component.

### 5. Use Realistic Data

Use data that closely resembles production data.

## Integration with Main App

### Shared Components

Components developed in workspaces should be easily usable in the main application:

```clojure
;; Component works in both workspaces and main app
(defsc Button [this {:keys [label variant] :as props}]
  {:query [:label :variant :onClick :disabled?]
   :initial-state {:label "" :variant :primary}}
  (dom/button 
    {:className (button-classes variant)
     :onClick (:onClick props)
     :disabled (:disabled? props)}
    label))
```

### State Management

Use the same state management patterns in workspaces as in the main app.

### Styling

Ensure workspace styles match the main application styles.

Workspaces provide a powerful development environment that accelerates component development, improves code quality, and creates living documentation for your Fulcro applications.