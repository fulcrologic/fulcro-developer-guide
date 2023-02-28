(ns book.queries.recursive-demo-bullets
  (:require [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
            [com.fulcrologic.fulcro.mutations :as m :refer [defmutation]]
            [com.fulcrologic.fulcro.dom :as dom]
            [clojure.string :as str]))

(declare ui-item)

(defsc Item [this {:keys [ui/checked? item/label item/subitems]}]
  {:query (fn [] [:ui/checked? :db/id :item/label {:item/subitems '...}])
   :ident [:item/by-id :db/id]}
  (dom/li
    (dom/input {:type     "checkbox"
                :checked  (if (boolean? checked?) checked? false)
                :onChange #(m/toggle! this :ui/checked?)})
    label
    (when subitems
      (dom/ul
        (mapv ui-item subitems)))))

(def ui-item (comp/factory Item {:keyfn :db/id}))

(defsc ItemList [this {:keys [db/id list/items] :as props}]
  {:query [:db/id {:list/items (comp/get-query Item)}]
   :ident [:list/by-id :db/id]}
  (dom/ul
    (mapv ui-item items)))

(def ui-item-list (comp/factory ItemList {:keyfn :db/id}))

(defsc Root [this {:keys [list]}]
  {:initial-state (fn [p]
                    {:list {:db/id      1
                            :list/items [{:db/id 2 :item/label "A"
                                          :item/subitems
                                                 [{:db/id      7
                                                   :item/label "A.1"
                                                   :item/subitems
                                                               [{:db/id         8
                                                                 :item/label    "A.1.1"
                                                                 :item/subitems []}]}]}
                                         {:db/id      3
                                          :item/label "B"
                                          :item/subitems
                                                      [{:db/id 6 :item/label "B.1"}]}
                                         {:db/id         4
                                          :item/label    "C"
                                          :item/subitems []}
                                         {:db/id         5
                                          :item/label    "D"
                                          ; just for fun..nest a dupe under D
                                          :item/subitems [{:db/id 6 :item/label "B.1"}]}]}})
   :query         [{:list (comp/get-query ItemList)}]}
  (dom/div
    (ui-item-list list)))
