(ns book.raw.fulcro-hooks
  (:require
    ["react" :as react]
    ["react-dom" :as rdom]
    [book.macros]
    [com.fulcrologic.fulcro.dom :as dom]
    [com.fulcrologic.fulcro.mutations :refer [defmutation]]
    [com.fulcrologic.fulcro.raw.application :as rapp]
    [com.fulcrologic.fulcro.raw.components :as rc]
    [com.fulcrologic.fulcro.react.hooks :as hooks]))

;; NOTE: RAW Fulcro App, even though we're using React, because we're not going to leverage
;; Fulcro's rendering integration, but instead are just writing a RAW function using hooks.
(defonce app (rapp/fulcro-app {:id "raw-fulcro-hooks"}))

;; Makes it possible to focus inspector from book
(swap! book.macros/app-registry assoc "raw-fulcro-hooks" app)

(defmutation make-older [{:person/keys [id]}]
  (action [{:keys [state]}]
    (swap! state update-in [:person/id id :person/age] inc)))

(defmutation change-street [{:address/keys [id street]}]
  (action [{:keys [state]}]
    (swap! state assoc-in [:address/id id :address/street] street)))

(def person-component
  (rc/nc [:person/id :person/name :person/age
          {:person/address [:address/id :address/street]}]
    {:initial-state (fn [{:keys [name address]}]
                      {:person/id      1
                       :person/name    name
                       :person/age     25
                       :person/address {:address/id     10
                                        :address/street address}})}))

(defn Person [props]
  (let [{:person/keys [id name age address]} (hooks/use-component app person-component
                                               {:keep-existing? true
                                                :initialize?    true
                                                :initial-params {:name "Bob" :address "111 Main"}})
        {:address/keys [street]} address]
    (let []
      (dom/div
        (dom/p (str name " lives at " street " and is " age " years old."))
        (dom/button
          {:onClick #(rc/transact! app [(make-older {:person/id id})])}
          (str "Make " name " older!"))
        (dom/button
          {:onClick #(rc/transact! app
                       [(change-street {:address/id     (:address/id address)
                                        :address/street (str
                                                          (rand-int 100)
                                                          " "
                                                          (rand-nth ["Broadway"
                                                                     "Main St."
                                                                     "3rd Ave."
                                                                     "Peach Ln."]))})])}
          (str "Move " name "!"))))))

(defn ui-person
  "A low-level plain React factory function."
  [] (react/createElement Person nil nil))

;; Render directly as a raw React component! The book has a div with that ID in this example block.
;; Mutations on the app will cause localized render of the dynamic content
(defonce _ (rdom/render (ui-person) (js/document.getElementById "raw-fulcro-hooks")))

