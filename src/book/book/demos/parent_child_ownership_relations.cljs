(ns book.demos.parent-child-ownership-relations
  (:require
    [com.fulcrologic.fulcro.dom :as dom]
    [com.fulcrologic.fulcro.mutations :as m]
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    [com.fulcrologic.fulcro.algorithms.merge :as merge]
    [taoensso.timbre :as log]))

; Not using an atom, so use a tree for app state (will auto-normalize via ident functions)
(def initial-state {:ui/react-key "abc"
                    :main-list    {:list/id    1
                                   :list/name  "My List"
                                   :list/items [{:item/id 1 :item/label "A"}
                                                {:item/id 2 :item/label "B"}]}})

(m/defmutation delete-item
  "Mutation: Delete an item from a list"
  [{:keys [list-id id]}]
  (action [{:keys [state]}]
    (log/info "Deleting item" id "from list" list-id)
    (swap! state
      (fn [s]
        (-> s
          (update :items dissoc id)
          (merge/remove-ident* [:items id] [:lists list-id :list/items]))))))

(defsc Item [this
             {:keys [item/id item/label] :as props}
             {:keys [on-delete] :as computed}]              ;; <1>
  {:initial-state (fn [{:keys [id label]}] {:item/id id :item/label label})
   :query         [:item/id :item/label]
   :ident         [:items :item/id]}
  (dom/li label (dom/button {:onClick #(on-delete id)} "X")))

(def ui-list-item (comp/factory Item {:keyfn :item/id}))

(defsc ItemList [this {:list/keys [id name items]}]
  {:initial-state (fn [p] {:list/id    1
                           :list/name  "List 1"
                           :list/items [(comp/get-initial-state Item {:id 1 :label "A"})
                                        (comp/get-initial-state Item {:id 2 :label "B"})]})
   :query         [:list/id :list/name {:list/items (comp/get-query Item)}]
   :ident         [:lists :list/id]}
  (let [delete-item (fn [item-id] (comp/transact! this [(delete-item {:list-id id :id item-id})])) ;; <2>
        item-props  (fn [i] (comp/computed i {:on-delete delete-item}))]
    (dom/div
      (dom/h4 name)
      (dom/ul
        (map #(ui-list-item (item-props %)) items)))))

(def ui-list (comp/factory ItemList))

(defsc Root [this {:keys [main-list]}]
  {:initial-state (fn [p] {:main-list (comp/get-initial-state ItemList {})})
   :query         [{:main-list (comp/get-query ItemList)}]}
  (dom/div (ui-list main-list)))


