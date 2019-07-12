(ns book.database
  (:require
    [cljs.spec.alpha :as s]
    [datascript.core :as d]
    [com.wsscode.pathom.connect :as pc]
    [com.wsscode.pathom.core :as p]
    [cljs.core.async :as async]
    clojure.pprint
    [taoensso.timbre :as log]))

(def schema {:person/id            {:db/unique :db.unique/identity}
             :person/spouse        {:db/cardinality :db.cardinality/one}
             :person/children      {:db/cardinality :db.cardinality/many}
             :person/addresses     {:db/cardinality :db.cardinality/many}
             :person/phone-numbers {:db/cardinality :db.cardinality/many}})

(defonce connection (d/create-conn schema))

(defn db [connection] (deref connection))

(defn seed-database []
  (d/transact! connection [{:db/id                1
                            :person/id            1
                            :person/age           45
                            :person/name          "Sally"
                            :person/spouse        2
                            :person/addresses     #{100}
                            :person/phone-numbers #{10 11}
                            :person/children      #{4 3}}

                           {:db/id        10
                            :phone/id     10
                            :phone/number "812-555-1212"
                            :phone/type   :work}

                           {:db/id        11
                            :phone/id     11
                            :phone/number "502-555-1212"
                            :phone/type   :home}

                           {:db/id        12
                            :phone/id     12
                            :phone/number "503-555-1212"
                            :phone/type   :work}

                           {:db/id                2
                            :person/id            2
                            :person/name          "Tom"
                            :person/age           48
                            :person/phone-numbers #{11 12}
                            :person/addresses     #{100}
                            :person/spouse        1
                            :person/children      #{3 4}}

                           {:db/id            3
                            :person/id        3
                            :person/age       17
                            :person/addresses #{100}
                            :person/name      "Amy"}

                           {:db/id            4
                            :person/id        4
                            :person/age       25
                            :person/addresses #{100}
                            :person/name      "Billy"
                            :person/children  #{5}}

                           {:db/id            5
                            :person/addresses #{100}
                            :person/id        5
                            :person/age       1
                            :person/name      "Billy Jr."}

                           {:db/id               100
                            :address/id          100
                            :address/street      "101 Main St"
                            :address/city        "Nowhere"
                            :address/state       "GA"
                            :address/postal-code "99999"}]))

(pc/defresolver person-resolver [{:keys [connection]} {:person/keys [id]}]
  {::pc/input  #{:person/id}
   ::pc/output [:person/name
                :person/age
                {:person/addresses [:address/id]}
                {:person/phone-numbers [:phone/id]}
                {:person/spouse [:person/id]}
                {:person/children [:person/id]}]}
  (d/pull (db connection)
    [:person/name
     :person/age
     {:person/addresses [:address/id]}
     {:person/phone-numbers [:phone/id]}
     {:person/spouse [:person/id]}
     {:person/children [:person/id]}]
    id))

(pc/defresolver all-people-resolver [{:keys [connection]} _]
  {::pc/output [{:all-people [:person/id]}]}
  {:all-people (mapv
                 (fn [id] {:person/id id})
                 (d/q '[:find [?e ...]
                        :where
                        [?e :person/id]]
                   (db connection)))})

(pc/defresolver address-resolver [{::p/keys [parent-query]
                                   :keys    [connection] :as env} {:address/keys [id]}]
  {::pc/input  #{:address/id}
   ::pc/output [:address/street :address/state :address/city :address/postal-code]}
  (d/pull (db connection) parent-query id))

(pc/defresolver phone-resolver [{::p/keys [parent-query]
                                 :keys    [connection] :as env} {:phone/keys [id]}]
  {::pc/input  #{:phone/id}
   ::pc/output [:phone/number :phone/type]}
  (d/pull (db connection) parent-query id))

;; Allow a person ID to resolve to (any) one of their addresses
(pc/defresolver default-address-resolver [{:keys [connection]} {:person/keys [id]}]
  {::pc/input  #{:person/id}
   ::pc/output [:address/id]}
  (let [address-id (d/q '[:find ?a .
                          :in $ ?pid
                          :where
                          [?pid :person/addresses ?addr]
                          [?addr :address/id ?a]]
                     (db connection) id)]
    (when address-id
      {:address/id address-id})))

;; Resolve :server/time-ms anywhere in a query. Allows us to timestamp a result.
(pc/defresolver time-resolver [_ _]
  {::pc/output [:server/time-ms]}
  {:server/time-ms (inst-ms (js/Date.))})

(def general-resolvers [phone-resolver address-resolver person-resolver
                        default-address-resolver
                        all-people-resolver time-resolver])

