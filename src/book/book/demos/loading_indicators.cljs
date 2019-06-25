(ns book.demos.loading-indicators
  (:require
    [com.fulcrologic.fulcro.dom :as dom]
    [com.fulcrologic.fulcro.data-fetch :as df]
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]] ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; CLIENT:
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(declare Item)

(defsc Item [this {:keys [db/id item/label] :as props}]
  ; query for the entire load marker table. use the lambda form of query for link queries
  {:query (fn [] [:db/id :item/label [df/marker-table '_]])
   :ident (fn [] [:lazy-load.items/by-id id])}
  (let [marker-id (keyword "item-marker" (str id))
        marker    (get-in props [df/marker-table marker-id])]
    (dom/div label
      ; If an item is rendered, and the fetch state is present, you can use helper functions from df namespace
      ; to provide augmented rendering.
      (if (df/loading? marker)
        (dom/span " (reloading...)")
        ; the `refresh!` function is a helper that can send an ident-based join query for a component.
        ; it is equivalent to `(load reconciler [:lazy-load.items/by-id id] Item)`, but finds the params
        ; using the component itself.
        (dom/button {:onClick #(df/refresh! this {:marker marker-id})} "Refresh")))))

(def ui-item (comp/factory Item {:keyfn :db/id}))

(defsc Child [this {:keys [child/label items] :as props}]
  {:query [:child/label {:items (comp/get-query Item)}]
   :ident (fn [] [:lazy-load/ui :child])}
  (let [render-list (fn [items] (map ui-item items))]
    (dom/div
      (dom/p "Child Label: " label)
      (if (seq items)
        (map ui-item items)
        (dom/button {:onClick #(df/load-field! this :items {:marker :child-marker})} "Load Items")))))

(def ui-child (comp/factory Child {:keyfn :child/label}))

(defsc Panel [this {:keys [ui/loading-data child] :as props}]
  {:initial-state (fn [params] {:child nil})
   :query         (fn [] [[:ui/loading-data '_] [df/marker-table '_] {:child (comp/get-query Child)}]) ; link querys require lambda
   :ident         (fn [] [:lazy-load/ui :panel])}
  (let [markers (get props df/marker-table)
        marker  (get markers :child-marker)]
    (dom/div
      (dom/div {:style {:float "right" :display (if loading-data "block" "none")}} "GLOBAL LOADING")
      (dom/div "This is the Panel")
      (if marker
        (dom/h4 "Loading child...")
        (if child
          (ui-child child)
          (dom/button {:onClick #(df/load-field! this :child {:marker :child-marker})} "Load Child"))))))

(def ui-panel (comp/factory Panel))

; Note: Kinda hard to do idents/lazy loading right on root...so generally just have root render simple layout
; and then render a child that has the rest.
(defsc Root [this {:keys [panel] :as props}]
  {:initial-state (fn [params] {:panel (comp/get-initial-state Panel nil)})
   :query         [{:panel (comp/get-query Panel)}]}
  (dom/div (ui-panel panel)))


