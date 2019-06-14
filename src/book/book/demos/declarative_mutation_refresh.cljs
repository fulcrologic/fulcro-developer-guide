(ns book.demos.declarative-mutation-refresh
  (:require
    [com.fulcrologic.fulcro.mutations :as m :refer [defmutation]]
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    [com.fulcrologic.fulcro.dom :as dom]
    [com.wsscode.pathom.connect :as pc]
    [taoensso.timbre :as log]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; SERVER:
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(pc/defmutation ping-right-mutation [env params]
  {::pc/sym `ping-right}
  (log/info "Server responding to ping")
  {:db/id 1 :right/value (random-uuid)})

(def resolvers [ping-right-mutation])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; CLIENT:
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defmutation ping-left [params]
  (action [{:keys [state]}]
    (swap! state update-in [:left/by-id 5 :left/value] inc))
  (refresh [env] [:left/value]))

(declare Right)

(defmutation ping-right [params]
  (remote [env]
    (m/returning env Right))
  (refresh [env] [:right/value]))

(defsc Left [this {:keys [db/id left/value]}]
  {:query         [:db/id :left/value]
   :initial-state {:db/id 5 :left/value 42}
   :ident         [:left/by-id :db/id]}
  (dom/div {:style {:float "left"}}
    (dom/button {:onClick #(comp/transact! this `[(ping-right {})])} "Ping Right")
    (str value)))

(def ui-left (comp/factory Left {:keyfn :db/id}))

(defsc Right [this {:keys [db/id right/value]}]
  {:query         [:db/id :right/value]
   :initial-state {:db/id 1 :right/value 99}
   :ident         [:right/by-id :db/id]}
  (dom/div {:style {:float "right"}}
    (dom/button {:onClick #(comp/transact! this `[(ping-left {})])} "Ping Left")
    (str value)))

(def ui-right (comp/factory Right {:keyfn :db/id}))

(defsc Root [this {:keys [left right]}]
  {:query         [{:left (comp/get-query Left)}
                   {:right (comp/get-query Right)}]
   :initial-state {:left {} :right {}}}
  (dom/div {:style {:width "600px" :height "50px"}}
    (ui-left left)
    (ui-right right)))


