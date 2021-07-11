(ns book.raw.dynamic-hooks
  (:require
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    [com.fulcrologic.fulcro.application :as app]
    [com.fulcrologic.fulcro.dom :as dom]
    [com.fulcrologic.fulcro.mutations :as m]
    [com.fulcrologic.fulcro.react.hooks :as hooks]))

(declare AltRootPlainClass app)

(defsc DynamicChild [this {:keys [:other/id :other/n] :as props}]
  {:query         [:other/id :other/n]
   :ident         :other/id
   :initial-state {:other/id :param/id :other/n :param/n}}
  (dom/div
    (dom/button
      {:onClick #(m/set-integer! this :other/n :value (inc n))}
      (str n))))

(def ui-dynamic-child (comp/factory DynamicChild {:keyfn :other/id}))

(defsc Container [this props]
  {:use-hooks? true}
  (let [id          (hooks/use-generated-id)                ; Generate a random ID
        other-props (hooks/use-component (comp/any->app this) DynamicChild
                      {:initialize?    true
                       :keep-existing? false
                       :initial-params {:id id :n 1}})]
    ;; Install a GC handler that will clean up the generated data of OtherChild when this component unmounts
    (hooks/use-gc this [:other/id id] #{})
    (ui-dynamic-child other-props)))

(def ui-container (comp/factory Container))

(defsc Root [this _]
  {}
  (let [show? (comp/get-state this :show?)]
    (dom/div
      (dom/button {:onClick (fn [] (comp/set-state! this {:show? (not show?)}))} "Toggle")
      (when show?
        (ui-container {})))))
