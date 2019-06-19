(ns book.demos.loading-data-basics
  (:require
    [com.fulcrologic.fulcro.data-fetch :as df]
    [book.demos.util :refer [now]]
    [com.fulcrologic.fulcro.mutations :as m]
    [com.fulcrologic.fulcro.dom :as dom]
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    [com.fulcrologic.fulcro.data-fetch :as df]
    [com.wsscode.pathom.connect :as pc]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; SERVER:
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def all-users {1 {:ldb.person/id 1 :ldb.person/name "A" :ldb.person/kind :friend}
                2 {:ldb.person/id 2 :ldb.person/name "B" :ldb.person/kind :friend}
                3 {:ldb.person/id 3 :ldb.person/name "C" :ldb.person/kind :enemy}
                4 {:ldb.person/id 4 :ldb.person/name "D" :ldb.person/kind :friend}})

(pc/defresolver sample-person-resolver [env {:person/keys [id]}]
  {::pc/input  #{:ldb.person/id}
   ::pc/output [:ldb.person/age-ms :ldb.person/name :ldb.person/kind]}
  (when-let [person (get all-users id)]
    (assoc person :ldb.person/age-ms (now))))

(pc/defresolver people-of-some-kind-resolver [env input]
  {::pc/output [{:ldb/people [:ldb.person/id]}]}
  (let [{:keys [kind]} (-> env :ast :params)]
    (let [result (into []
                   (comp
                     (filter (fn [p] (= kind (:ldb.person/kind p))))
                     (map (fn [p] (select-keys p [:ldb.person/id]))))
                   (vals all-users))]
      {:ldb/people result})))

(def resolvers [sample-person-resolver people-of-some-kind-resolver])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; CLIENT:
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defsc Person [this {:ldb.person/keys [id name age-ms] :as props}]
  {:query [:ldb.person/id :ldb.person/name :ldb.person/age-ms]
   :ident :ldb.person/id}
  (dom/li
    (str name " (last queried at " age-ms ")")
    (dom/button {:onClick (fn []
                            ; Load relative to an ident (of this component).
                            ; This will refresh the entity in the db. The helper function
                            ; (df/refresh! this) is identical to this, but shorter to write.
                            (df/load! this (comp/ident this props) Person))} "Update")))

(def ui-person (comp/factory Person {:keyfn :db/id}))

(defsc People [this {:list/keys [people]}]
  {:initial-state (fn [{:keys [kind]}] {:list/id kind :list/people []})
   :query         [:list/id {:list/people (comp/get-query Person)}]
   :ident         :list/id}
  (dom/ul
    (map ui-person people)))

(def ui-people (comp/factory People {:keyfn :people/kind}))

(defsc Root [this {:root/keys [friends enemies]}]
  {:initial-state (fn [{:keys [kind]}] {:root/friends (comp/get-initial-state People {:id :friends})
                                        :root/enemies (comp/get-initial-state People {:id :enemies})})
   :query         [{:root/enemies (comp/get-query People)} {:root/friends (comp/get-query People)}]}
  (dom/div
    (dom/h4 "Friends")
    (ui-people friends)
    (dom/h4 "Enemies")
    (ui-people enemies)))

(defn initialize
  "To be used in :started-callback to pre-load things."
  [app]
  ; This is a sample of loading a list of people into a given target, including
  ; use of params. The generated network query will result in params
  ; appearing in the server-side query, and :people will be the dispatch
  ; key. The subquery will also be available (from Person). See the server code above.
  (df/load! app :ldb/people Person {:target [:list/id :enemies :list/people]
                                    :params {:kind :enemy}})
  (df/load! app :ldb/people Person {:target [:list/id :friends :list/people]
                                    :params {:kind :friend}}))

