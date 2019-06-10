(ns book.queries.union-example-1
  (:require
    [com.fulcrologic.fulcro.dom :as dom :refer [div]]
    [com.fulcrologic.fulcro.routing.union-router :as r :refer [defsc-router]]
    [book.elements :as ele]
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]))

(defn item-ident
  "Generate an ident from a person, place, or thing."
  [props] [(:kind props) (:db/id props)])

(defn item-key
  "Generate a distinct react key for a person, place, or thing"
  [props] (str (:kind props) "-" (:db/id props)))

(defn make-person [id n] {:db/id id :kind :person/by-id :person/name n})
(defn make-place [id n] {:db/id id :kind :place/by-id :place/name n})
(defn make-thing [id n] {:db/id id :kind :thing/by-id :thing/label n})

(defsc PersonDetail [this {:keys [db/id person/name] :as props}]
  ; defsc-router expects there to be an initial state for each possible target. We'll cause this to be a "no selection"
  ; state so that the detail screen that starts out will show "Nothing selected". We initialize all three in case
  ; we later re-order them in the defsc-router.
  {:ident         (fn [] (item-ident props))
   :query         [:kind :db/id :person/name]
   :initial-state {:db/id :no-selection :kind :person/by-id}}
  (dom/div
    (if (= id :no-selection)
      "Nothing selected"
      (str "Details about person " name))))

(defsc PlaceDetail [this {:keys [db/id place/name] :as props}]
  {:ident         (fn [] (item-ident props))
   :query         [:kind :db/id :place/name]
   :initial-state {:db/id :no-selection :kind :place/by-id}}
  (dom/div
    (if (= id :no-selection)
      "Nothing selected"
      (str "Details about place " name))))

(defsc ThingDetail [this {:keys [db/id thing/label] :as props}]
  {:ident         (fn [] (item-ident props))
   :query         [:kind :db/id :thing/label]
   :initial-state {:db/id :no-selection :kind :thing/by-id}}
  (dom/div
    (if (= id :no-selection)
      "Nothing selected"
      (str "Details about thing " label))))

(defsc PersonListItem [this
                       {:keys [db/id person/name] :as props}
                       {:keys [onSelect] :as computed}]
  {:ident (fn [] (item-ident props))
   :query [:kind :db/id :person/name]}
  (dom/li {:onClick #(onSelect (item-ident props))}
    (dom/a {} (str "Person " id " " name))))

(def ui-person (comp/factory PersonListItem {:keyfn item-key}))

(defsc PlaceListItem [this {:keys [db/id place/name] :as props} {:keys [onSelect] :as computed}]
  {:ident (fn [] (item-ident props))
   :query [:kind :db/id :place/name]}
  (dom/li {:onClick #(onSelect (item-ident props))}
    (dom/a {} (str "Place " id " : " name))))

(def ui-place (comp/factory PlaceListItem {:keyfn item-key}))

(defsc ThingListItem [this {:keys [db/id thing/label] :as props} {:keys [onSelect] :as computed}]
  {:ident (fn [] (item-ident props))
   :query [:kind :db/id :thing/label]}
  (dom/li {:onClick #(onSelect (item-ident props))}
    (dom/a {} (str "Thing " id " : " label))))

(def ui-thing (comp/factory ThingListItem item-key))

(defsc-router ItemDetail [this props]
  {:router-id      :detail-router
   :ident          (fn [] (item-ident props))
   :default-route  PersonDetail
   :router-targets {:person/by-id PersonDetail
                    :place/by-id  PlaceDetail
                    :thing/by-id  ThingDetail}}
  (dom/div "No route"))

(def ui-item-detail (comp/factory ItemDetail))

(defsc ItemUnion [this {:keys [kind] :as props}]
  {:ident (fn [] (item-ident props))
   :query (fn [] {:person/by-id (comp/get-query PersonListItem)
                  :place/by-id  (comp/get-query PlaceListItem)
                  :thing/by-id  (comp/get-query ThingListItem)})}
  (case kind
    :person/by-id (ui-person props)
    :place/by-id (ui-place props)
    :thing/by-id (ui-thing props)))

(def ui-item-union (comp/factory ItemUnion {:keyfn item-key}))

(defsc ItemList [this {:keys [items]} {:keys [onSelect]}]
  {
   :initial-state (fn [p]
                    ; These would normally be loaded...but for demo purposes we just hand code a few
                    {:items [(make-person 1 "Tony")
                             (make-thing 2 "Toaster")
                             (make-place 3 "New York")
                             (make-person 4 "Sally")
                             (make-thing 5 "Pillow")
                             (make-place 6 "Canada")]})
   :ident         (fn [] [:lists/by-id :singleton])
   :query         [{:items (comp/get-query ItemUnion)}]}
  (dom/ul :.ui.list
    (map (fn [i] (ui-item-union (comp/computed i {:onSelect onSelect}))) items)))

(def ui-item-list (comp/factory ItemList))

(defsc Root [this {:keys [item-list item-detail]}]
  {:query         [{:item-list (comp/get-query ItemList)}
                   {:item-detail (comp/get-query ItemDetail)}]
   :initial-state (fn [p] (merge
                            (r/routing-tree
                              (r/make-route :detail [(r/router-instruction :detail-router [:param/kind :param/id])]))
                            {:item-list   (comp/get-initial-state ItemList nil)
                             :item-detail (comp/get-initial-state ItemDetail nil)}))}
  (let [; This is the only thing to do: Route the to the detail screen with the given route params!
        showDetail (fn [[kind id]]
                     (comp/transact! this `[(r/route-to {:handler :detail :route-params {:kind ~kind :id ~id}})]))]
    ; devcards, embed in iframe so we can use bootstrap css easily
    (dom/div {:key "example-frame-key"}
      (dom/style ".boxed {border: 1px solid black}")
      ;(dom/link {:rel "stylesheet" :href "https://cdnjs.cloudflare.com/ajax/libs/semantic-ui/2.4.1/semantic.min.css"})
      (dom/table :.ui.table {}
        (dom/tr
          (dom/th "Items")
          (dom/th "Detail"))
        (dom/tr
          (dom/td (ui-item-list (comp/computed item-list {:onSelect showDetail})))
          (dom/td (ui-item-detail item-detail)))))))
