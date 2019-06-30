(ns book.demos.dynamic-ui-main
  (:require
    [com.fulcrologic.fulcro.components :refer [defsc]]
    [com.fulcrologic.fulcro.routing.legacy-ui-routers :as r]
    [com.fulcrologic.fulcro.dom :as dom]
    cljs.loader
    [taoensso.timbre :as log]))

; This is a "screen" that we want to load with code-splitting modules. See the "demos" build in project.clj. The name
; of the module needs to match the first element of the ident, as that's how the dynamic router figures out what module
; to load.
(defsc Main [this {:keys [label main-prop]}]
  {:query         [r/dynamic-route-key :label :main-prop]
   :initial-state (fn [params] {r/dynamic-route-key :ui-main :label "MAIN" :main-prop "main page data"})
   :ident         (fn [] [:ui-main :singleton])}
  (dom/div {:style {:backgroundColor "red"}}
    (str label " " main-prop)))

(defn ^:export init []
  (log/info "dynamic ui main loaded"))

(defmethod r/get-dynamic-router-target :ui-main [k] Main)
(cljs.loader/set-loaded! :ui-main)

