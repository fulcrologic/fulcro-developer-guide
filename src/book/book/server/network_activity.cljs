(ns book.server.network-activity
  (:require
    [com.fulcrologic.fulcro.application :as app]
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    [com.fulcrologic.fulcro.dom :as dom]
    [com.fulcrologic.fulcro.mutations :as m :refer [defmutation]]
    [com.wsscode.pathom.connect :as pc]
    [taoensso.timbre :as log]
    [clojure.pprint :refer [pprint]]
    [com.fulcrologic.fulcro.data-fetch :as df]))

;; Simulated server

(pc/defresolver silly-resolver [_ _]
  {::pc/output [::data]}
  {::data 42})

;; Client

(defsc ActivityIndicator [this props]
  {:query         [[::app/active-remotes '_]]
   :ident         (fn [] [:component/id ::activity])
   :initial-state {}}
  (let [active-remotes (::app/active-remotes props)]
    (dom/div
      (dom/h3 "Active Remotes")
      (dom/pre (pr-str active-remotes)))))

(def ui-activity-indicator (comp/factory ActivityIndicator {:keyfn :id}))

(defsc Root [this {:keys [indicator]}]
  {:query         [{:indicator (comp/get-query ActivityIndicator)}]
   :initial-state {:indicator {}}}
  (dom/div {}
    (dom/p {} "Use the server controls to slow down the network, so you can see the activity")
    (dom/button {:onClick #(df/load! this ::data nil)} "Trigger a Load")
    (ui-activity-indicator indicator)))

