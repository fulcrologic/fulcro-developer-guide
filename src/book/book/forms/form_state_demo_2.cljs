(ns book.forms.form-state-demo-2
  (:require
    [com.fulcrologic.semantic-ui.modules.dropdown.ui-dropdown :as dropdown]
    [com.fulcrologic.fulcro.mutations :as m :refer [defmutation]]
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    [com.fulcrologic.fulcro.dom :as dom]
    [com.fulcrologic.fulcro.algorithms.form-state :as fs]
    [com.fulcrologic.fulcro.algorithms.tempid :as tempid]
    [com.fulcrologic.fulcro.data-fetch :as df]
    [clojure.string :as str]
    [cljs.spec.alpha :as s]
    [com.wsscode.pathom.connect :as pc]
    [book.elements :as ele]
    [taoensso.timbre :as log]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Server Code
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

; a simple query for any person, that will return valid-looking data
(pc/defresolver person-resolver [env {:person/keys [id]}]
  {::pc/input  #{:person/id}
   ::pc/output [:person/name :person/age :person/phone-numbers]}
  {:person/id            id
   :person/name          (str "User " id)
   :person/age           56
   :person/phone-numbers [{:phone/id 1 :phone/number "555-111-1212" :phone/type :work}
                          {:phone/id 2 :phone/number "555-333-4444" :phone/type :home}]})

(defonce id (atom 1000))
(defn next-id [] (swap! id inc))

; Server submission...just prints delta for demo, and remaps tempids (forms with tempids are always considered dirty)
(pc/defmutation submit-person-mutation [env inputs]
  {::pc/sym `submit-person}
  (let [params (-> env :ast :params)]
    (js/console.log "Server received form submission with content: ")
    (cljs.pprint/pprint params)
    (let [ids    (map (fn [[k v]] (second k)) (:diff params))
          remaps (into {} (keep (fn [v] (when (tempid/tempid? v) [v (next-id)])) ids))]
      {:tempids remaps})))

(def resolvers [person-resolver submit-person-mutation])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Client Code
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(s/def :person/name (s/and string? #(seq (str/trim %))))
(s/def :person/age #(s/int-in-range? 1 120 %))

(defn field-attrs
  "A helper function for getting aspects of a particular field."
  [component field]
  (let [form         (comp/props component)
        entity-ident (comp/get-ident component form)
        id           (str (first entity-ident) "-" (second entity-ident))
        is-dirty?    (fs/dirty? form field)
        clean?       (not is-dirty?)
        validity     (fs/get-spec-validity form field)
        is-invalid?  (= :invalid validity)
        value        (get form field "")]
    {:dirty?   is-dirty?
     :ident    entity-ident
     :id       id
     :clean?   clean?
     :validity validity
     :invalid? is-invalid?
     :value    value}))

(def integer-fields #{:person/age})

(defn input-with-label
  [component field label validation-message input]
  (let [{:keys [dirty? invalid?]} (field-attrs component field)]
    (comp/fragment
      (dom/div :.field {:classes [(when invalid? "error") (when dirty? "warning")]}
        (dom/label {:htmlFor (str field)} label)
        input)
      (when invalid?
        (dom/div :.ui.error.message {} validation-message))
      (when dirty?
        (dom/div :.ui.warning.message {} "(dirty)")))))

(s/def :phone/number #(re-matches #"\(?[0-9]{3}[-.)]? *[0-9]{3}-?[0-9]{4}" %))

(defsc PhoneForm [this {:phone/keys [id number type] :as props}]
  {:query       [:phone/id :phone/number :phone/type fs/form-config-join]
   :form-fields #{:phone/number :phone/type}
   :ident       :phone/id}
  (dom/div :.ui.segment
    (dom/div :.ui.form
      (input-with-label this :phone/number "Phone:" "10-digit phone number is required."
        (dom/input {:value    (or (str number) "")
                    :onBlur   #(comp/transact! this [(fs/mark-complete! {:entity-ident [:phone/id id]
                                                                         :field        :phone/number})])
                    :onChange #(m/set-string! this :phone/number :event %)}))
      (input-with-label this :phone/type "Type:" ""
        (dropdown/ui-dropdown {:value     (name type)
                               :selection true
                               :options   [{:text "Home" :value "home"}
                                           {:text "Work" :value "work"}]
                               :onChange  (fn [_ v]
                                            (when-let [v (some-> (.-value v) keyword)]
                                              (m/set-value! this :phone/type v)
                                              (comp/transact! this [(fs/mark-complete! {:field :phone/type})])))})))))

(def ui-phone-form (comp/factory PhoneForm {:keyfn :phone/id}))

(defn add-phone*
  "Add the given phone info to a person."
  [state-map phone-id person-id type number]
  (let [phone-ident      [:phone/id phone-id]
        new-phone-entity {:phone/id phone-id :phone/type type :phone/number number}]
    (-> state-map
      (update-in [:person/id person-id :person/phone-numbers] (fnil conj []) phone-ident)
      (assoc-in phone-ident new-phone-entity))))

(defmutation add-phone
  "Mutation: Add a phone number to a person, and initialize it as a working form."
  [{:keys [person-id]}]
  (action [{:keys [state]}]
    (let [phone-id (tempid/tempid)]
      (swap! state (fn [s]
                     (-> s
                       (add-phone* phone-id person-id :home "")
                       (fs/add-form-config* PhoneForm [:phone/id phone-id])))))))

(defsc PersonForm [this {:person/keys [id name age phone-numbers]}]
  {:query       [:person/id :person/name :person/age
                 {:person/phone-numbers (comp/get-query PhoneForm)}
                 fs/form-config-join]
   :form-fields #{:person/name :person/age :person/phone-numbers} ; phone-numbers here becomes a subform because it is a join in the query.
   :ident       :person/id}
  (dom/div :.ui.form
    (input-with-label this :person/name "Name:" "Name is required."
      (dom/input {:value    (or name "")
                  :onBlur   #(comp/transact! this [(fs/mark-complete! {:entity-ident [:person/id id]
                                                                       :field        :person/name})
                                                   :root/person])
                  :onChange (fn [evt]
                              (m/set-string! this :person/name :event evt))}))
    (input-with-label this :person/age "Age:" "Age must be between 1 and 120"
      (dom/input {:value    (or age "")
                  :onBlur   #(comp/transact! this [(fs/mark-complete! {:entity-ident [:person/id id]
                                                                       :field        :person/age})
                                                   :root/person])
                  :onChange #(m/set-integer! this :person/age :event %)}))
    (dom/h4 "Phone numbers:")
    (when (seq phone-numbers)
      (map ui-phone-form phone-numbers))
    (dom/button :.ui.button {:onClick #(comp/transact! this `[(add-phone {:person-id ~id})])} "+")))

(def ui-person-form (comp/factory PersonForm {:keyfn :person/id}))

(defn add-person*
  "Add a person with the given details to the state database."
  [state-map id name age]
  (let [person-ident [:person/id id]
        person       {:person/id id :person/name name :person/age age}]
    (assoc-in state-map person-ident person)))

(defmutation edit-new-person [_]
  (action [{:keys [state]}]
    (let [person-id    (tempid/tempid)
          person-ident [:person/id person-id]
          phone-id     (tempid/tempid)]
      (swap! state
        (fn [s] (-> s
                  (add-person* person-id "" 0)
                  (add-phone* phone-id person-id :home "")
                  (assoc :root/person person-ident)         ; join it into the UI as the person to edit
                  (fs/add-form-config* PersonForm [:person/id person-id])))))))

(defmutation edit-existing-person
  "Turn an existing person with phone numbers into an editable form with phone subforms."
  [{:keys [person-id]}]
  (action [{:keys [state]}]
    (swap! state
      (fn [s] (-> s
                (assoc :root/person [:person/id person-id])
                (fs/add-form-config* PersonForm [:person/id person-id]) ; will not re-add config to entities that were present
                (fs/entity->pristine* [:person/id person-id]) ; in case we're re-loading it, make sure the pristine copy it up-to-date
                ;; it just came from server, so all fields should be valid
                (fs/mark-complete* [:person/id person-id]))))))

(defmutation submit-person [{:keys [id]}]
  (action [{:keys [state]}]
    (swap! state fs/entity->pristine* [:person/id id]))
  (remote [env] true))

(defsc Root [this {:keys [root/person]}]
  {:query         [{:root/person (comp/get-query PersonForm)}]
   :initial-state (fn [params] {})}
  (ele/ui-iframe {:frameBorder 0 :width 800 :height 820}
    (dom/div :.ui.container.segments
      (dom/link {:rel "stylesheet" :href "https://cdnjs.cloudflare.com/ajax/libs/semantic-ui/2.4.1/semantic.min.css"})
      (dom/button :.ui.button
        {:onClick #(df/load! this [:person/id 21] PersonForm {:target               [:root/person]
                                                              :post-mutation        `edit-existing-person
                                                              :post-mutation-params {:person-id 21}})}
        "Simulate Edit (existing) Person from Server")
      (dom/button :.ui.buton {:onClick #(comp/transact! this `[(edit-new-person {})])} "Simulate New Person Creation")

      (when (:person/id person)
        (dom/div :.ui.segment
          (ui-person-form person)))

      (dom/div :.ui.segment
        (dom/button :.ui.button {:onClick  #(comp/transact! this `[(fs/reset-form! {:form-ident [:person/id ~(:person/id person)]})])
                                 :disabled (not (fs/dirty? person))} "Reset")
        (dom/button :.ui.button {:onClick  #(comp/transact! this `[(submit-person {:id ~(:person/id person) :diff ~(fs/dirty-fields person false)})])
                                 :disabled (or
                                             (fs/invalid-spec? person)
                                             (not (fs/dirty? person)))} "Submit")))))
