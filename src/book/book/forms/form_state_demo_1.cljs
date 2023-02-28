(ns book.forms.form-state-demo-1
  (:require
    [com.fulcrologic.semantic-ui.modules.dropdown.ui-dropdown :as dropdown]
    [book.elements :as ele]
    [com.fulcrologic.fulcro.mutations :as m :refer [defmutation]]
    [com.fulcrologic.fulcro.algorithms.form-state :as fs]
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    [com.fulcrologic.fulcro.dom :as dom]
    [cljs.spec.alpha :as s]
    [taoensso.timbre :as log]))

(declare Root PhoneForm)

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

(s/def :phone/number #(re-matches #"\(?[0-9]{3}[-.)]? *[0-9]{3}-?[0-9]{4}" %))

(defmutation abort-phone-edit [{:keys [id]}]
  (action [{:keys [state]}]
    (swap! state (fn [s]
                   (-> s
                     ; stop editing
                     (dissoc :root/phone)
                     ; revert to the pristine state
                     (fs/pristine->entity* [:phone/id id])))))
  (refresh [env] [:root/phone]))

(defmutation submit-phone [{:keys [id delta]}]
  (action [{:keys [state]}]
    (swap! state (fn [s]
                   (-> s
                     ; stop editing
                     (dissoc :root/phone)
                     ; update the pristine state
                     (fs/entity->pristine* [:phone/id id])))))
  (remote [env] true)
  (refresh [env] [:root/phone [:phone/id id]]))

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

(defsc PhoneForm [this {:phone/keys [id type number] :as props}]
  {:query       [:phone/id :phone/type :phone/number fs/form-config-join]
   :form-fields #{:phone/number :phone/type}
   :ident       :phone/id}
  (let [dirty?   (fs/dirty? props)
        invalid? (= :invalid (fs/get-spec-validity props))]
    (dom/div :.ui.form {:classes [(when invalid? "error") (when dirty? "warning")]}
      (input-with-label this :phone/number "Phone:" "10-digit phone number is required."
        (dom/input {:value    (or (str number) "")
                    :onChange #(m/set-string! this :phone/number :event %)}))
      (input-with-label this :phone/type "Type:" ""
        (dropdown/ui-dropdown {:value     (name type)
                               :selection true
                               :options   [{:text "Home" :value "home"}
                                           {:text "Work" :value "work"}]
                               :onChange  (fn [_ v]
                                            (when-let [v (some-> (.-value v) keyword)]
                                              (m/set-value! this :phone/type v)))}))
      (dom/button :.ui.button {:onClick #(comp/transact! this [(abort-phone-edit {:id id})])} "Cancel")
      (dom/button :.ui.button {:disabled (or (not (fs/checked? props)) (fs/invalid-spec? props))
                               :onClick  #(comp/transact! this [(submit-phone {:id id :delta (fs/dirty-fields props true)})])} "Commit!"))))

(def ui-phone-form (comp/factory PhoneForm {:keyfn :phone/id}))

(defsc PhoneNumber [this {:phone/keys [id type number]} {:keys [onSelect]}]
  {:query         [:phone/id :phone/number :phone/type]
   :initial-state {:phone/id :param/id :phone/number :param/number :phone/type :param/type}
   :ident         :phone/id}
  (dom/li :.ui.item
    (dom/a {:onClick (fn [] (onSelect id))}
      (str number " (" (get {:home "Home" :work "Work" nil "Unknown"} type) ")"))))

(def ui-phone-number (comp/factory PhoneNumber {:keyfn :phone/id}))

(defsc PhoneBook [this {:phonebook/keys [id phone-numbers]} {:keys [onSelect]}]
  {:query         [:phonebook/id {:phonebook/phone-numbers (comp/get-query PhoneNumber)}]
   :initial-state {:phonebook/id            :main
                   :phonebook/phone-numbers [{:id 1 :number "541-555-1212" :type :home}
                                             {:id 2 :number "541-555-5533" :type :work}]}
   :ident         :phonebook/id}
  (dom/div
    (dom/h4 "Phone Book (click a number to edit)")
    (dom/ul
      (mapv (fn [n] (ui-phone-number (comp/computed n {:onSelect onSelect}))) phone-numbers))))

(def ui-phone-book (comp/factory PhoneBook {:keyfn :phonebook/id}))

(defmutation edit-phone-number [{:keys [id]}]
  (action [{:keys [state]}]
    (let [phone-type (get-in @state [:phone/id id :phone/type])]
      (swap! state (fn [s]
                     (-> s
                       ; make sure the form config is with the entity
                       (fs/add-form-config* PhoneForm [:phone/id id])
                       ; since we're editing an existing thing, we should start it out complete (validations apply)
                       (fs/mark-complete* [:phone/id id])
                       ; tell the root UI that we're editing a phone number by linking it in
                       (assoc :root/phone [:phone/id id])))))))

(defsc Root [this {:keys [:root/phone :root/phonebook]}]
  {:query         [{:root/phonebook (comp/get-query PhoneBook)}
                   {:root/phone (comp/get-query PhoneForm)}]
   :initial-state {:root/phonebook {}
                   :root/phone     {}}}
  (ele/ui-iframe {:frameBorder 0 :width 500 :height 400}
    (dom/div
      (dom/link {:rel "stylesheet" :href "https://cdnjs.cloudflare.com/ajax/libs/semantic-ui/2.4.1/semantic.min.css"})
      (if (contains? phone :phone/number)
        (ui-phone-form phone)
        (ui-phone-book (comp/computed phonebook {:onSelect (fn [id] (comp/transact! this [(edit-phone-number {:id id})]))}))))))
