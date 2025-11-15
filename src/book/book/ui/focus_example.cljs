(ns book.ui.focus-example
  (:require
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    [com.fulcrologic.fulcro.dom :as dom]
    [com.fulcrologic.fulcro.mutations :as m]
    [com.fulcrologic.fulcro.react.hooks :as hooks]
    [goog.object :as gobj]))

(defsc ClickToEditField [this {:keys [value editing?]}]
  {:initial-state {:value    "ABC"
                   :db/id    1
                   :editing? false}
   :query         [:db/id :value :editing?]
   :ident         [:field/by-id :db/id]
   :use-hooks?    true}
  (let [^js ref (hooks/use-ref nil)
        focus!  (fn [] (when-let [input-field (.-current ref)] (.focus input-field)))
        select! (fn [] (when-let [input-field (.-current ref)]
                         (.focus input-field)
                         (.setSelectionRange input-field 0 (.. input-field -value -length))))]
    (dom/div
      (dom/a {:onClick focus!} "Click to focus (if not already editing): ")
      (dom/input {:value    value
                  :onChange #(m/set-string! this :value :event %)
                  :ref      ref})
      (dom/button {:onClick select!} "Highlight All"))))

(def ui-click-to-edit (comp/factory ClickToEditField))

(defsc Root [this {:keys [field] :as props}]
  {:query         [{:field (comp/get-query ClickToEditField)}]
   :initial-state {:field {}}}
  (ui-click-to-edit field))
