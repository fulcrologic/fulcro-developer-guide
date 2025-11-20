
# Forms and Validation

## Overview

Fulcro provides comprehensive form support through the Form State library (`com.fulcrologic.fulcro.algorithms.form-state`), enabling complex form validation, field-level state management, and integration with the normalized database. The modern API uses `clojure.spec.alpha` for validation and automatic dirty/pristine state tracking.

## Core Concepts

### Form State Benefits
- **Field-level tracking**: Individual field modification detection via pristine/dirty state
- **Spec-based validation**: Automatic validation using `clojure.spec.alpha` predicates
- **Nested forms**: Complex forms with sub-forms, each with independent validation
- **Dirty detection**: Automatically know what fields have been modified from pristine state
- **Tri-state validation**: Fields track `:valid`, `:invalid`, or unchecked status
- **Normalization compatible**: Works seamlessly with Fulcro's graph database

### How Form State Works

Form state maintains **two copies** of entity data:
- **Entity**: Current working state (what the user has edited)
- **Pristine**: Last known good state (for detecting changes and reverting)

The form system compares these to determine if fields are dirty. When you load an entity into a form, it copies to pristine. When the user saves successfully, entity is copied to pristine.

### Form Configuration in Queries

Form state is included in a component's query via `fs/form-config-join`:

```clojure
(require '[com.fulcrologic.fulcro.algorithms.form-state :as fs])

(defsc PersonForm [this {:person/keys [id name email] :as props}]
  {:query       [:person/id :person/name :person/email fs/form-config-join]
   :ident       :person/id
   :form-fields #{:person/name :person/email}}
  ; render body
)
```

The `:form-fields` set tells Fulcro which keys to track for dirty/pristine state. The `fs/form-config-join` adds form metadata to the query (validation state, marked-complete status, etc.).

## Basic Form Setup

### Form Component

```clojure
(defsc PersonForm [this {:person/keys [id name email] :as props}]
  {:query       [:person/id :person/name :person/email fs/form-config-join]
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
        {:disabled (or (not (fs/checked? props)) invalid?)
         :onClick  #(comp/transact! this [(save-person {:person-id id})])}
        "Save"))))
```

### Form Initialization

Form state must be initialized when you create or load an entity for editing. Use `fs/add-form-config*` (note the `*`) as a state swap function:

```clojure
(defmutation edit-person [{:keys [person-id]}]
  (action [{:keys [state]}]
    (swap! state
      (fn [s]
        (-> s
          (fs/add-form-config* PersonForm [:person/id person-id])
          (assoc :root/form [:person/id person-id])))))  ; Show form UI
  ; Optionally load from server:
  (remote [env] true))
```

After initialization:
- `fs/add-form-config*` sets up validation state for fields
- Entity and pristine copies are separate in the state map
- Component receives entity data; form validation metadata is separate

### Default Values

When creating a new entity for a form:

```clojure
(defmutation create-new-person [_]
  (action [{:keys [state]}]
    (let [person-id (tempid/tempid)]
      (swap! state
        (fn [s]
          (-> s
            (assoc-in [:person/id person-id]
              {:person/id person-id
               :person/name ""
               :person/email ""})
            (fs/add-form-config* PersonForm [:person/id person-id])
            (assoc :root/form [:person/id person-id])))))))
```

## Field Mutations

### Built-in Field Mutations

Located in `com.fulcrologic.fulcro.mutations`:

```clojure
;; String fields - change on event or from explicit value
(m/set-string! this :person/name :event event)
(m/set-string! this :person/name :value "New Name")

;; Integer fields
(m/set-integer! this :person/age :event event)
(m/set-integer! this :person/age :value 25)

;; Any value (dropdowns, objects, etc.)
(m/set-value! this :person/status :active)
```

### Usage in Input Elements

```clojure
;; Text input
(dom/input {:value    (or name "")
            :onChange #(m/set-string! this :person/name :event %)})

;; Number input
(dom/input {:type     "number"
            :value    (or age 0)
            :onChange #(m/set-integer! this :person/age :event %)})

;; Select/dropdown
(dom/select {:value    (or status "")
             :onChange #(m/set-value! this :person/status (keyword (.-value %)))})

;; Checkbox
(dom/input {:type     "checkbox"
            :checked  (boolean active)
            :onChange #(m/set-value! this :person/active (.-checked %))})
```

### Custom Field Updates

For complex field updates, create a custom mutation:

```clojure
(defmutation update-field [{:keys [field value]}]
  (action [{:keys [state]}]
    (swap! state update-in field (constantly value))))
```

## Validation

### Spec-Based Validation

Modern Fulcro uses `clojure.spec.alpha` for validation. Define specs for your fields:

```clojure
(require '[clojure.spec.alpha :as s])

;; Simple string validation
(s/def :person/name (s/and string? #(seq (str/trim %))))

;; Email validation
(s/def :person/email
  (s/and string?
         #(re-matches #"[^@]+@[^@]+\.[^@]+" %)))

;; Integer range (1 to 120 exclusive)
(s/def :person/age (s/int-in 1 120))

;; Composite spec for entire entity
(s/def :person (s/keys :req [:person/id :person/name :person/email]))
```

**How it works**: When a field changes via `m/set-string!` or similar, Fulcro automatically checks the field value against its spec. The validation state (`:valid`, `:invalid`, or unchecked) is stored in form state.

### Validation Checking

Check validation state at the form or field level:

```clojure
;; Field-level checks
(fs/get-spec-validity props :person/name)   ; Returns :valid, :invalid, or nil (unchecked)
(= :invalid (fs/get-spec-validity props :person/email))

;; Form-level checks
(fs/invalid-spec? props)      ; Returns true if any field is invalid
(fs/checked? props)           ; Returns true if all fields have been checked

;; Pristine state
(fs/dirty? props)             ; Form has unsaved changes
(fs/dirty? props :person/name) ; Specific field has changes
```

### Complex Validation with Specs

For multi-field validation rules:

```clojure
(s/def :person/password-match
  (s/and (s/keys :req [:person/password :person/password-confirm])
         #(= (:person/password %) (:person/password-confirm %))))

;; In component:
(let [password-match? (s/valid? :person/password-match props)]
  (when-not password-match?
    (dom/div :.error "Passwords don't match")))
```

## Form State Management

### Pristine/Dirty Tracking

The form system maintains separate entity and pristine states:

```clojure
;; Check dirty status
(fs/dirty? props)              ; Entire form is dirty
(fs/dirty? props :person/name) ; Specific field is dirty
(not (fs/dirty? props))        ; Form is pristine (no changes)

;; Get changed fields
(fs/dirty-fields props)        ; Map of fields that changed
(fs/dirty-fields props true)   ; Include tempids
```

### Managing Pristine State

After loading or saving, update pristine to mark form as clean:

```clojure
;; When user saves successfully:
(defmutation save-person [{:keys [person-id]}]
  (action [{:keys [state]}]
    (swap! state
      (fn [s]
        (-> s
          (fs/entity->pristine* [:person/id person-id])))))  ; Entity becomes pristine
  (remote [env] true))

;; When user cancels editing:
(defmutation cancel-edit [{:keys [person-id]}]
  (action [{:keys [state]}]
    (swap! state
      (fn [s]
        (-> s
          (dissoc :root/form)                                ; Hide form
          (fs/pristine->entity* [:person/id person-id])))))  ; Restore from pristine
```

### Field Validation Completion

Mark fields as "checked" to show validation errors:

```clojure
;; Mark a single field as checked
(comp/transact! this [(fs/mark-complete! {:entity-ident [:person/id person-id]
                                          :field :person/name})])

;; Mark entire form as checked
(comp/transact! this [(fs/mark-complete! {:entity-ident [:person/id person-id]})])

;; Without explicit ident (when form is root):
(comp/transact! this [(fs/mark-complete! {:field :person/name})])
```

### Reset Form to Pristine

Discard all edits and reset form to original state:

```clojure
(comp/transact! this [(fs/reset-form! {:form-ident [:person/id person-id]})])
```

## Nested Forms

### Nested Form Structure

Parent components can include child forms. Child components are queried via joins and have their own form configs:

```clojure
(defsc PhoneForm [this {:phone/keys [id type number] :as props}]
  {:query       [:phone/id :phone/type :phone/number fs/form-config-join]
   :ident       :phone/id
   :form-fields #{:phone/type :phone/number}}
  (dom/div
    (dom/input {:value    (or number "")
                :onChange #(m/set-string! this :phone/number :event %)})))

(defsc PersonForm [this {:person/keys [name phone-numbers] :as props}]
  {:query       [:person/id :person/name
                 {:person/phone-numbers (comp/get-query PhoneForm)}
                 fs/form-config-join]
   :ident       :person/id
   :form-fields #{:person/name :person/phone-numbers}}  ; Include nested field
  (dom/div
    (dom/input {:value    (or name "")
                :onChange #(m/set-string! this :person/name :event %)})
    (dom/h4 "Phone Numbers:")
    (mapv ui-phone-form phone-numbers)))
```

### Adding Nested Items

When adding new nested items, initialize their form config:

```clojure
(defmutation add-phone-number [{:keys [person-id]}]
  (action [{:keys [state]}]
    (let [phone-id (tempid/tempid)
          new-phone {:phone/id phone-id :phone/type :home :phone/number ""}]
      (swap! state
        (fn [s]
          (-> s
            (merge/merge-component PhoneForm new-phone)  ; Add entity
            (fs/add-form-config* PhoneForm [:phone/id phone-id])  ; Init form
            (update-in [:person/id person-id :person/phone-numbers]
              (fnil conj []) [:phone/id phone-id]))))))  ; Link to parent
```

**Important**: Each nested form component must have `fs/form-config-join` in its query and `:form-fields` in its options.

### Nested Form Validation

Validation happens independently per form. The parent can check the entire tree:

```clojure
(let [person-invalid? (fs/invalid-spec? person)
      phone-invalid?  (some #(fs/invalid-spec? %) phone-numbers)]
  (dom/button {:disabled (or person-invalid? phone-invalid?)}
    "Save"))
```

The dirty state of nested forms contributes to the parent's dirty detection.

## Advanced Form Features

### Multi-Step Forms

Track current step and conditionally render forms:

```clojure
(defsc MultiStepForm [this {:keys [form-step] :as props}]
  {:query       [:form/id :form-step
                 {:form-step-1 (comp/get-query Step1Form)}
                 {:form-step-2 (comp/get-query Step2Form)}
                 fs/form-config-join]
   :ident       :form/id
   :form-fields #{:form-step :form-step-1 :form-step-2}}
  (case form-step
    1 (ui-step1-form (:form-step-1 props))
    2 (ui-step2-form (:form-step-2 props))
    (dom/div "Complete")))

;; Advance steps (don't proceed if validation fails)
(defmutation advance-step [{:keys [form-id current-step]}]
  (action [{:keys [state]}]
    (let [step-key (keyword (str "form-step-" current-step))]
      (when-not (fs/invalid-spec? (get-in @state [:form/id form-id step-key]))
        (swap! state update-in [:form/id form-id :form-step] inc)))))
```

### Dynamic Field Addition

Add fields dynamically based on user actions:

```clojure
(defmutation add-address [{:keys [person-id]}]
  (action [{:keys [state]}]
    (let [address-id (tempid/tempid)
          new-address {:address/id address-id :address/street "" :address/city ""}]
      (swap! state
        (fn [s]
          (-> s
            (merge/merge-component AddressForm new-address)
            (fs/add-form-config* AddressForm [:address/id address-id])
            (update-in [:person/id person-id :person/addresses]
              (fnil conj []) [:address/id address-id])))))))
```

### File Upload Fields

File inputs require special handling since browsers don't allow direct file access via value:

```clojure
(defsc FileUploadField [this {:keys [file/name file/size] :as props}]
  {:query       [:file/id :file/name :file/size fs/form-config-join]
   :ident       :file/id
   :form-fields #{:file/name :file/size}}
  (dom/div
    (dom/input {:type     "file"
                :accept   "image/*"
                :onChange (fn [evt]
                            (let [file (-> evt .-target .-files (aget 0))]
                              (when file
                                (m/set-value! this :file/name (.-name file))
                                (m/set-value! this :file/size (.-size file))
                                ;; Handle actual file data separately - 
                                ;; typically upload to server in mutation
                                )))})
    (when name
      (dom/span "Selected: " name))))
```

## Form Submission

### Basic Submission

Check validation before submitting:

```clojure
(defmutation save-person [{:keys [person-id]}]
  (action [{:keys [state]}]
    (let [person (get-in @state [:person/id person-id])]
      ;; Verify form is valid before attempting save
      (when (fs/checked? person)
        (swap! state fs/entity->pristine* [:person/id person-id]))))
  ;; Send to server
  (remote [env] true))

;; In component:
(dom/button {:disabled (or (not (fs/checked? props)) (fs/invalid-spec? props))
             :onClick  #(comp/transact! this [(save-person {:person-id id})])}
  "Save")
```

### Submission with Delta

Only send changed fields to server:

```clojure
(defmutation submit-form [{:keys [person-id delta]}]
  (action [{:keys [state]}]
    (let [person (get-in @state [:person/id person-id])]
      (when (fs/checked? person)
        (swap! state fs/entity->pristine* [:person/id person-id]))))
  (remote [env] true)
  ;; Server receives only changed fields
  (refresh [env] [:person/id person-id]))

;; In component - pass dirty fields
(dom/button {:onClick #(comp/transact! this
              [(submit-form {:person-id id
                            :delta (fs/dirty-fields props false)})])}
  "Save")
```

### Handling Server Errors

When the server returns validation errors, update form state to reflect them:

```clojure
(defmutation save-person [{:keys [person-id]}]
  (action [{:keys [state]}]
    (swap! state fs/entity->pristine* [:person/id person-id]))
  (remote [env] true)
  (error-action [{:keys [result]}]
    ;; Server error response contains field errors
    ;; Restore entity from pristine since save failed
    (swap! state
      (fn [s]
        (fs/pristine->entity* s [:person/id person-id])))))
```

## Integration Patterns

### Forms with Dynamic Router

Load form data within route lifecycle:

```clojure
(defsc EditPersonPage [this props]
  {:route-segment ["person" :person-id "edit"]
   :will-enter    (fn [app {:keys [person-id]}]
                    (dr/route-deferred [:person/id person-id]
                      #(df/load! app [:person/id person-id] EditPersonPage
                          {:post-mutation edit-person})))}
  (let [person (comp/props this)]
    (dom/div
      (ui-person-form person))))

(defmutation edit-person [{:keys [person-id]}]
  (action [{:keys [state]}]
    (swap! state
      (fn [s]
        (-> s
          (fs/add-form-config* PersonForm [:person/id person-id])
          (assoc :root/form [:person/id person-id]))))))
```

### Forms with State Machines (UISM)

Manage form lifecycle with Fulcro's actor model:

```clojure
(defstatemachine form-machine
  {::uism/states
   {:viewing
    {::uism/events
     {:event/edit {:target :editing}}}

    :editing
    {::uism/events
     {:event/save   {:handler save-handler}
      :event/cancel {:handler cancel-handler}}}

    :saving
    {::uism/events
     {:event/save-success {:target :viewing}
      :event/save-error   {:target :editing}}}}})

(defn save-handler [env]
  (let [form-ident (uism/actor->ident env :form)]
    (if (fs/invalid-spec? (get-in @(::uism/state-map env) form-ident))
      ;; Validation failed - stay in editing
      env
      ;; Send to server
      (-> env
        (uism/trigger-remote-mutation 'save-person
          {:person-id (second form-ident)
           :on-ok     :event/save-success
           :on-error  :event/save-error})))))

(defn cancel-handler [env]
  (let [form-ident (uism/actor->ident env :form)]
    (-> env
      (uism/apply-action #(fs/pristine->entity* % form-ident))
      (uism/goto-state :viewing))))
```

## Best Practices

### Form Design
- **Keep forms focused**: Each form handles one entity or logical group
- **Use appropriate field mutations**: `set-string!` for text, `set-integer!` for numbers, `set-value!` for complex types
- **Initialize form config**: Always call `fs/add-form-config*` when creating/loading entities for editing
- **Include `fs/form-config-join`**: All form components must include this in their query

### Validation Strategy
- **Define specs early**: Create specs for all form fields before creating the form component
- **Use spec predicates**: Leverage `s/and`, `s/int-in`, `re-matches`, etc.
- **Mark fields complete selectively**: Use `fs/mark-complete!` on blur or submit, not on every keystroke
- **Client-side is convenience**: Server validation is authoritative; always validate on server too
- **Meaningful error messages**: Validation tells you a field is invalid, but specs don't provide messages—add custom UI to display helpful text

### State Management
- **Maintain pristine copy**: Only use `fs/entity->pristine*` when save succeeds
- **Restore on cancel**: Use `fs/pristine->entity*` to discard changes
- **Track dirty state**: Use `fs/dirty?` and `fs/dirty-fields` for conditional saves
- **Nested forms have independent state**: Each nested component manages its own form config

### Performance
- **Avoid deep nested forms**: Each nested level adds complexity to dirty tracking
- **Lazy validate**: Check `fs/checked?` to avoid showing errors prematurely
- **Debounce server saves**: If auto-saving, debounce to avoid excessive requests
- **Normalize form entities**: Use standard idents (`[:person/id 1]`) for proper normalization

---
