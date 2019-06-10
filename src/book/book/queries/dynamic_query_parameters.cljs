(ns book.queries.dynamic-query-parameters
  (:require
    [com.fulcrologic.fulcro.dom :as dom]
    [goog.object]
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]))

; This component has a query parameter that can be set to whatever we want dynamically
(defsc Leaf [this {:keys [x y] :as props}]
  {:initial-state (fn [params] {:x 1 :y 99})
   :query         (fn [] '[:x ?additional-stuff])           ; the parameter ?additional-stuff starts out empty
   :ident         (fn [] [:LEAF :ID])}
  (dom/div
    (dom/button  {:onClick (fn [] (comp/set-query! this Leaf {:params {:additional-stuff :y}}))} "Add :y to query")
    (dom/button  {:onClick (fn [] (comp/set-query! this Leaf {:params {}}))} "Drop :y from query")
    (dom/ul
      (dom/li  "x: " x)
      (dom/li  "y: " y))))

(def ui-leaf (comp/factory Leaf))

(defsc Root [this {:keys [root/leaf] :as props}]
  {:initial-state (fn [p] {:root/leaf (comp/get-initial-state Leaf {})})
   :query         (fn [] [{:root/leaf (comp/get-query ui-leaf)}])}
  (dom/div  (ui-leaf leaf)))
