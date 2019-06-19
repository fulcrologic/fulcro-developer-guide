(ns book.ui.focus-example
  (:require [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
            [com.fulcrologic.fulcro.dom :as dom]
            [com.fulcrologic.fulcro.mutations :as m]
            [goog.object :as gobj]))

(defsc ClickToEditField [this {:keys [value editing?]}]
  {:initial-state      {:value    "ABC"
                        :db/id    1
                        :editing? false}
   :query              [:db/id :value :editing?]
   :ident              [:field/by-id :db/id]
   :initLocalState     (fn [this]
                         {:save-ref (fn [r] (gobj/set this "input-ref" r))})
   :componentDidUpdate (fn [this prev-props _]
                         (when (and (not (:editing? prev-props)) (:editing? (comp/props this)))
                           (when-let [input-field (gobj/get this "input-ref")]
                             (.focus input-field))))}
  (let [save-ref (comp/get-state this :save-ref)]
    (dom/div
      ; trigger a focus based on a state change (componentDidUpdate)
      (dom/a {:onClick #(m/toggle! this :editing?)}
        "Click to focus (if not already editing): ")
      (dom/input {:value    value
                  :onChange #(m/set-string! this :event %)
                  :ref      save-ref})
      ; do an explicit focus
      (dom/button {:onClick (fn []
                              (when-let [input-field (gobj/get this "input-ref")]
                                (.focus input-field)
                                (.setSelectionRange input-field 0 (.. input-field -value -length))))}
        "Highlight All"))))

(def ui-click-to-edit (comp/factory ClickToEditField))

(defsc Root [this {:keys [field] :as props}]
  {:query         [{:field (comp/get-query ClickToEditField)}]
   :initial-state {:field {}}}
  (ui-click-to-edit field))
