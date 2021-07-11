(ns book.raw.adding-components
  (:require
    [com.fulcrologic.fulcro.raw.application :as app]
    [com.fulcrologic.fulcro.raw.components :as rc]
    [com.fulcrologic.fulcro.raw.application :as rapp]
    [com.fulcrologic.fulcro.algorithms.merge :as merge]))

(def Address (rc/nc [:address/id :address/street]
               {:componentName ::Address
                :initial-state (fn [{:keys [id street]}] {:address/id id :address/street street})
                :ident         (fn [this props] [:address/by-id (:address/id props)])}))

(def Person (rc/nc [:person/id :person/name {:person/address (rc/get-query Address)}]
              {:componentName ::Person
               :initial-state (fn [_]
                                {:person/id      10
                                 :person/name    "Bob"
                                 :person/address (rc/get-initial-state Address
                                                   {:id     20
                                                    :street "111 Main"})})}))

(def app (rapp/fulcro-app))

(comment
  (rapp/add-component! app Person {:initialize?    true
                                   :keep-existing? true
                                   :receive-props  (fn [props]
                                                     (js/console.log props))})
  ;; Console output:
  ;;{:person/id 10, :person/name "Bob", :person/address {:address/id 20, :address/street "111 Main"}

  (merge/merge-component! app Address {:address/id     20
                                       :address/street "50 Broadway"})
  ;; Console output:
  ;;{:person/id 10, :person/name "Bob", :person/address {:address/id 20, :address/street "50 Broadway"}

  (merge/merge-component! app Address {:address/id     20
                                       :address/street "50 Broadway"})
  ;; NO CONSOLE OUTPUT. Props didn't *actually* change

  )
