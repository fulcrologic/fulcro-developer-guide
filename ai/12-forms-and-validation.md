# Forms and Validation

## Overview
Fulcro provides comprehensive form support through the Form State library, enabling complex form validation, field-level state management, and integration with the normalized database.

## Core Concepts

### Form State Benefits
- **Field-level tracking**: Individual field validation and state
- **Nested forms**: Complex forms with sub-forms
- **Validation integration**: Built-in and custom validators
- **Pristine/dirty tracking**: Know what's been modified
- **Validation timing**: On change, blur, or submit
- **Normalization friendly**: Works with Fulcro's graph database

### Form State Structure
```clojure
;; Form state is added to component data
{:person/name     "John"
 :person/email    "john@example.com"
 ::fs/config      {:validation-fn validate-person}
 ::fs/fields      {:person/name  {::fs/validation-message "Name is required"}
                   :person/email {::fs/valid? true}}}
```

## Basic Form Setup

### Form Component
```clojure
(defsc PersonForm [this {:person/keys [id name email] :as props}]
  {:query       [:person/id :person/name :person/email ::fs/config ::fs/fields]
   :ident       :person/id
   :form-fields #{:person/name :person/email}}
  (let [invalid? (fs/invalid-spec? props)]
    (dom/form
      (dom/div
        (dom/label "Name:")
        (dom/input {:value    (or name "")
                    :onChange #(m/set-string! this :person/name :event %)}))
      (dom/div
        (dom/label "Email:")
        (dom/input {:value    (or email "")
                    :onChange #(m/set-string! this :person/email :event %)}))
      (dom/button
        {:disabled invalid?
         :onClick  #(comp/transact! this [(save-person {})])}
        "Save"))))
```

### Form Initialization
```clojure
;; Add form configuration to initial state
(comp/get-initial-state PersonForm
  (fs/add-form-config PersonForm
    {:person/id    1
     :person/name  ""
     :person/email ""}))
```

## Field Mutations

### Built-in Field Mutations
```clojure
;; String fields
(m/set-string! this :person/name :event event)
(m/set-string! this :person/name :value "New Name")

;; Integer fields
(m/set-integer! this :person/age :event event)

;; Boolean fields
(m/toggle! this :person/active)

;; Generic value setting
(m/set-value! this :person/status :value :active)
```

### Custom Field Updates
```clojure
(defmutation update-field
  [{:keys [field value]}]
  (action [{:keys [component]}]
    (fs/update-forms-field! component field value)))
```

## Validation

### Built-in Validators
```clojure
(defn validate-person [form field]
  (let [v (get form field)]
    (case field
      :person/name (cond
                     (str/blank? v) "Name is required"
                     (< (count v) 2) "Name must be at least 2 characters")
      :person/email (when-not (valid-email? v)
                      "Please enter a valid email")
      nil)))

;; Add validation to form config
{::fs/config {:validation-fn validate-person}}
```

### Validation with Spec
```clojure
(require '[clojure.spec.alpha :as s])

(s/def ::non-empty-string (s/and string? #(not (str/blank? %))))
(s/def ::email (s/and string? #(re-matches #".+@.+\..+" %)))
(s/def ::person (s/keys :req [:person/name :person/email]))

(defn spec-validation [form field]
  (when-not (s/valid? (keyword "domain" (name field)) (get form field))
    "Invalid value"))
```

### Field-Level Validation
```clojure
;; Check specific field validity
(fs/valid? props :person/name)
(fs/invalid? props :person/email)

;; Get validation message
(fs/validation-message props :person/name)

;; Check entire form
(fs/valid-spec? props)
(fs/invalid-spec? props)
```

## Form State Management

### Pristine/Dirty Tracking
```clojure
;; Check if form has been modified
(fs/dirty? props)
(fs/pristine? props)

;; Check specific fields
(fs/dirty? props :person/name)
(fs/pristine? props :person/email)

;; Reset form to pristine state
(fs/mark-complete! this)
```

### Form Lifecycle
```clojure
;; Mark form as complete (pristine)
(fs/mark-complete! this)

;; Reset form to original values
(comp/transact! this [(fs/reset-form! {})])

;; Add new form configuration
(fs/add-form-config PersonForm initial-data)
```

## Nested Forms

### Nested Form Structure
```clojure
(defsc Address [this {:address/keys [street city state] :as props}]
  {:query       [:address/id :address/street :address/city :address/state
                 ::fs/config ::fs/fields]
   :ident       :address/id
   :form-fields #{:address/street :address/city :address/state}}
  (dom/div
    (dom/input {:value    (or street "")
                :onChange #(m/set-string! this :address/street :event %)})
    (dom/input {:value    (or city "")
                :onChange #(m/set-string! this :address/city :event %)})
    (dom/input {:value    (or state "")
                :onChange #(m/set-string! this :address/state :event %)})))

(defsc PersonForm [this {:person/keys [name address] :as props}]
  {:query       [:person/id :person/name
                 {:person/address (comp/get-query Address)}
                 ::fs/config ::fs/fields]
   :ident       :person/id
   :form-fields #{:person/name}}
  (dom/div
    (dom/input {:value    (or name "")
                :onChange #(m/set-string! this :person/name :event %)})
    (ui-address address)))
```

### Nested Form Validation
```clojure
(defn validate-person-with-address [form field]
  (case field
    :person/name (when (str/blank? (get form field))
                   "Name is required")
    ;; Address validation handled by Address component
    nil))
```

## Advanced Form Features

### Multi-step Forms
```clojure
(defsc MultiStepForm [this {:keys [current-step] :as props}]
  {:query [:form/id :current-step
           {:step1-data (comp/get-query Step1Form)}
           {:step2-data (comp/get-query Step2Form)}]}
  (case current-step
    1 (ui-step1-form (:step1-data props))
    2 (ui-step2-form (:step2-data props))
    (dom/div "Complete")))
```

### Dynamic Field Addition
```clojure
(defmutation add-phone-number
  [{:keys [person-id]}]
  (action [{:keys [state]}]
    (let [new-phone {:phone/id (tempid/tempid) :phone/number ""}]
      (swap! state
        (fn [s]
          (-> s
            (merge/merge-component Phone new-phone)
            (update-in [:person/id person-id :person/phones]
              (fnil conj []) [:phone/id (:phone/id new-phone)])))))))
```

### File Upload Fields
```clojure
(defsc FileUploadField [this {:keys [file/name file/data] :as props}]
  {:form-fields #{:file/name}}
  (dom/div
    (dom/input {:type     "file"
                :onChange (fn [evt]
                            (let [file (-> evt .-target .-files (aget 0))]
                              (m/set-value! this :file/name :value (.-name file))
                              ;; Handle file data separately
                              ))})
    (when name
      (dom/span "Selected: " name))))
```

## Form Submission

### Basic Submission
```clojure
(defmutation save-person
  [{:keys [person-id]}]
  (action [{:keys [state]}]
    (let [person (get-in @state [:person/id person-id])]
      (if (fs/valid-spec? person)
        ;; Submit to server
        (log/info "Saving person" person)
        (log/warn "Form has validation errors"))))
  (remote [env] true))
```

### Submission with Validation
```clojure
(defmutation submit-form
  [{:keys [form-ident]}]
  (action [{:keys [app state]}]
    (let [form-data (get-in @state form-ident)]
      (if (fs/valid-spec? form-data)
        (do
          ;; Mark as submitting
          (fs/mark-fields-complete! app form-ident)
          ;; Trigger remote save
          )
        ;; Show validation errors
        (fs/validate-fields! app form-ident)))))
```

### Handling Server Errors
```clojure
(defmutation save-person
  [params]
  (action [env] ...)
  (remote [env] true)
  (error-action [{:keys [component]}]
    ;; Mark field errors from server response
    (fs/mark-field-error! component :person/email "Email already exists")))
```

## Integration Patterns

### Forms with UISM
```clojure
(defstatemachine form-machine
  {::uism/states
   {:editing
    {::uism/events
     {:event/save   {:handler save-handler}
      :event/cancel {:handler cancel-handler}}}

    :saving
    {::uism/events
     {:event/save-complete {:target :viewing}
      :event/save-failed   {:target :editing}}}}})

(defn save-handler [env]
  (let [form-ident (uism/actor->ident env :form)]
    (if (fs/valid-spec? (get-in @(::uism/state-map env) form-ident))
      (-> env
        (uism/trigger-remote-mutation 'save-form
          {:form-data form-ident
           :on-ok     :event/save-complete
           :on-error  :event/save-failed}))
      ;; Stay in editing, show validation errors
      env)))
```

### Forms with Dynamic Router
```clojure
(defsc EditPersonPage [this props]
  {:route-segment ["person" :person-id "edit"]
   :will-enter    (fn [app {:keys [person-id]}]
                    (dr/route-deferred [:person/id person-id]
                      #(df/load! app [:person/id person-id] EditPersonPage)))}
  (ui-person-form props))
```

## Best Practices

### Form Design
- **Keep forms focused**: One concern per form
- **Use appropriate field types**: String, integer, boolean mutations
- **Validate early**: Show errors as user types when appropriate
- **Provide clear feedback**: Loading states, success messages

### Validation Strategy
- **Client-side first**: Immediate feedback for user
- **Server-side authoritative**: Final validation on server
- **Progressive validation**: More validation as user progresses
- **Meaningful messages**: Clear, actionable error messages

### State Management
- **Track pristine state**: Know what's been modified
- **Handle concurrent edits**: Server-side conflict resolution
- **Auto-save considerations**: Balance UX with server load
- **Undo/redo support**: Maintain operation history

### Performance
- **Debounce validation**: Don't validate on every keystroke
- **Lazy load options**: Large dropdown data loaded on demand
- **Optimize re-renders**: Use React.memo for expensive form fields
- **Normalize form data**: Store in graph database efficiently