(ns book.simple-router-1
  (:require [com.fulcrologic.fulcro.routing.union-router :as r :refer-macros [defsc-router]]
            [com.fulcrologic.fulcro.dom :as dom]
            [fulcro.client :as fc]
            [com.fulcrologic.fulcro.data-fetch :as df]
            [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
            [com.fulcrologic.fulcro.mutations :as m]))

(defsc Index [this {:keys [db/id router/page]}]
  {:query         [:db/id :router/page]
   :ident         (fn [] [page id])                         ; IMPORTANT! Look up both things, don't use the shorthand for idents on screens!
   :initial-state {:db/id 1 :router/page :PAGE/index}}
  (dom/div nil "Index Page"))

(defsc Settings [this {:keys [db/id router/page]}]
  {:query         [:db/id :router/page]
   :ident         (fn [] [page id])
   :initial-state {:db/id 1 :router/page :PAGE/settings}}
  (dom/div "Settings Page"))

(defsc-router RootRouter [this {:keys [router/page db/id]}]
  {:router-id      :root/router
   :default-route  Index
   :ident          (fn [] [page id])
   :router-targets {:PAGE/index    Index
                    :PAGE/settings Settings}}
  (dom/div "Bad route"))

(def ui-root-router (comp/factory RootRouter))

(defsc Root [this {:keys [router]}]
  {:initial-state (fn [p] {:router (comp/get-initial-state RootRouter {})})
   :query         [{:router (comp/get-query RootRouter)}]}
  (dom/div
    (dom/a {:onClick #(comp/transact! this
                        `[(r/set-route {:router :root/router
                                        :target [:PAGE/index 1]})])} "Index") " | "
    (dom/a {:onClick #(comp/transact! this
                        `[(r/set-route {:router :root/router
                                        :target [:PAGE/settings 1]})])} "Settings")
    (ui-root-router router)))


