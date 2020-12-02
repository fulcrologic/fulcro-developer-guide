(ns book.example-1
  (:require
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    [com.fulcrologic.fulcro.mutations :refer [defmutation]]
    [com.fulcrologic.fulcro.dom :as dom]))

(defmutation bump-number [ignored]
  (action [{:keys [state]}]
    (swap! state update :ui/number inc)))

(defsc Root [this {:ui/keys [number]}]
  {:query         [:ui/number]
   :initial-state {:ui/number 0}}
  (dom/div
    (dom/h4 "This is an example.")
    (dom/button {:onClick #(comp/transact! this [(bump-number {})])}
      "You've clicked this button " number " times.")))
