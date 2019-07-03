(ns book.demos.loading-data-basics
  (:require
    [com.fulcrologic.fulcro.data-fetch :as df]
    [book.demos.util :refer [now]]
    [com.fulcrologic.fulcro.mutations :as m]
    [com.fulcrologic.fulcro.dom :as dom]
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    [com.fulcrologic.fulcro.data-fetch :as df]))

(defsc Person [this {:person/keys [id name age] :server/keys [time-ms] :as props}]
  {:query [:person/id :person/name :person/age :server/time-ms]
   :ident :person/id}
  (dom/li
    (str name " (last queried at " time-ms ")")
    (dom/button {:onClick (fn []
                            ; Load relative to an ident (of this component).
                            ; This will refresh the entity in the db. The helper function
                            ; (df/refresh! this) is identical to this, but shorter to write.
                            (df/load! this (comp/get-ident this) Person))} "Update")))

(def ui-person (comp/factory Person {:keyfn :db/id}))

(defsc People [this {:list/keys [people]}]
  {:initial-state {:list/id :param/id :list/people []}
   :query         [:list/id {:list/people (comp/get-query Person)}]
   :ident         :list/id}
  (dom/ul
    (map ui-person people)))

(def ui-people (comp/factory People {:keyfn :people/kind}))

(defsc Root [this {:root/keys [list]}]
  {:initial-state (fn [_] {:root/list (comp/get-initial-state People {:id :people})})
   :query         [{:root/list (comp/get-query People)}]}
  (dom/div
    (dom/button
      {:onClick (fn []
                  (df/load! this :all-people Person {:target [:list/id :people :list/people]}))}
      "Load People")
    (dom/h4 "People in Database")
    (ui-people list)))

