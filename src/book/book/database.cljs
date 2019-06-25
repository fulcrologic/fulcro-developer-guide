(ns book.database
  (:require
    [cljs.spec.alpha :as s]
    [datascript.core :as d]
    [com.wsscode.pathom.connect :as pc]))

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
                            :person/spouse        1
                            :person/children      #{3 4}}

                           {:db/id       3
                            :person/id   3
                            :person/age  17
                            :person/name "Amy"}

                           {:db/id           4
                            :person/id       4
                            :person/age      25
                            :person/name     "Billy"
                            :person/children #{5}}

                           {:db/id       5
                            :person/id   5
                            :person/age  1
                            :person/name "Billy Jr."}]))

(pc/defresolver person-resolver [{:keys [connection]} {:person/keys [id]}]
  {::pc/input  #{:person/id}
   ::pc/output [:person/name
                {:person/spouse [:person/id]}
                {:person/children [:person/id]}]}
  (d/pull (db connection)
    '[:person/name
      {:person/spouse [:person/id]}
      {:person/children [:person/id]}] id))

(pc/defresolver all-people-resolver [{:keys [connection]} input]
  {::pc/output {:all-people [:person/id]}}
  (d/q '[:find [?e ...]
         :where
         [?e :person/id]]
    (db connection)))

(def general-resolvers [person-resolver all-people-resolver])
