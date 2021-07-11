(ns book.raw.normalizing-components
  (:require
    [com.fulcrologic.fulcro.raw.components :as rc]))

(def Address (rc/nc [:address/id :address/street]
               {:componentName ::Address
                :initial-state (fn [{:keys [id street]}] {:address/id id :address/street street})
                :ident         (fn [this props] [:address/by-id (:address/id props)])}))

(def Person (rc/nc [:person/id :person/name {:person/address (rc/get-query Address)}]))

(comment
  (rc/get-ident Address {:address/id 1})
  ;; => [:address/by-id 1]
  (rc/get-query Address)
  ;; => [:address/id :address/street]
  (rc/get-initial-state Address {:id 42 :street "111 Main"})
  ;; => #:address{:id 42, :street "111 Main"}
  (rc/component-name Address)
  ;; => "book.raw.normalizing-components/Address"
  (rc/get-query Person)
  ;; => [:person/id :person/name #:person{:address [:address/id :address/street]}]

  )