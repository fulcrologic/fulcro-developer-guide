(ns book.dynamic-router-example
  (:require
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    [com.fulcrologic.fulcro.routing.dynamic-routing :as dr :refer [defrouter]]
    [com.fulcrologic.fulcro.dom :as dom]
    [taoensso.timbre :as log]))

(defsc Settings [this props]
  {:ident         (fn [] [:component/id ::settings])
   :query         [:settings]
   :initial-state {:settings "stuff"}
   :route-segment ["settings"]
   :will-enter    (fn [app route-params]
                    (log/info "Will enter settings" route-params)
                    (dr/route-immediate [:component/id ::settings]))
   :will-leave    (fn [this props]
                    (js/console.log (comp/get-ident this) "props" props)
                    true)}
  (dom/div "Settings"))

(defsc Main [this props]
  {:ident         (fn [] [:component/id ::main])
   :query         [:main]
   :initial-state {:main "stuff"}
   :route-segment ["main"]
   :will-enter    (fn [app route-params]
                    (log/info "Will enter main" route-params)
                    (dr/route-immediate [:component/id ::main]))
   :will-leave    (fn [this props]
                    (log/info (comp/get-ident this) "props" props)
                    true)}
  (dom/div "Main"))

(defrouter TopRouter [this props]
  {:router-targets [Main Settings]})

(def ui-top-router (comp/factory TopRouter))

(defsc Root [this {:root/keys [router]}]
  {:query         [{:root/router (comp/get-query TopRouter)}]
   :initial-state {:root/router {}}}
  (dom/div
    (dom/button {:onClick #(dr/change-route this ["main"])} "Go to main")
    (dom/button {:onClick #(dr/change-route this ["settings"])} "Go to settings")
    (ui-top-router router)))
