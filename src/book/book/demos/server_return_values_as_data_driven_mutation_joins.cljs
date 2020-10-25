(ns book.demos.server-return-values-as-data-driven-mutation-joins
  (:require
    [com.fulcrologic.fulcro.dom :as dom]
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    [com.fulcrologic.fulcro.dom :as dom]
    [com.fulcrologic.fulcro.mutations :as m :refer [defmutation]]
    [fulcro.client :as fc]
    [fulcro.server :as server]
    [com.fulcrologic.fulcro.data-fetch :as df]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; SERVER:
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(server/defquery-root :mutation-join-list
  (value [env {:keys [kind]}]
    {:db/id      1
     :list/title "Mutation Join List Demo"
     :list/items [{:db/id      1
                   :item/value "A"}
                  {:db/id      2
                   :item/value "B"}]}))

(server/defmutation change-label [{:keys [db/id item/value]}]
  (action [env]
    {:db/id id :item/value (str (random-uuid))}))

(def ids (atom 999))

(server/defmutation add-item [{:keys [id value]}]
  (action [env]
    (let [new-id (swap! ids inc)]
      (merge
        {::comp/tempids {id new-id}}
        {:db/id id :item/value value}))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; CLIENT:
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(declare Item)

(defmutation change-label [{:keys [db/id]}]
  (remote [env]
    (-> env
      (m/returning Item)
      (m/with-params {:db/id id}))))

(defn set-overlay-visible* [state-map visible?] (assoc-in state-map [:overlay :visible?] visible?))
(defmutation set-overlay [{:keys [:visible?]}] (action [{:keys [state]}] (swap! state set-overlay-visible* visible?)))

(defmutation add-item [{:keys [list-id id value]}]
  (action [{:keys [state]}]
    (let [idnt [:item/by-id id]]
      (swap! state
        (fn [s]
          (-> s
            (assoc-in idnt {:db/id id :item/value value})
            (set-overlay-visible* true)
            (fc/integrate-ident idnt :append [:list/by-id list-id :list/items]))))))
  (remote [env]
    (m/returning Item)))

(defsc Item [this {:keys [db/id item/value]}]
  {:query [:db/id :item/value]
   :ident [:item/by-id :db/id]}
  (dom/li {:onClick (fn [evt]
                      (comp/transact! this `[(change-label {:db/id ~id})]))} value))

(def ui-item (comp/factory Item {:keyfn :db/id}))

(def example-height "400px")

(defsc ItemList [this {:keys [db/id list/title list/items] :as props}]
  {:query         [:db/id :list/title {:list/items (comp/get-query Item)}]
   :initial-state {}
   :ident         [:list/by-id :db/id]}
  (dom/div {:style {:width "600px" :height example-height}}
    (dom/h3 title)
    (dom/ul (map ui-item items))
    (dom/button {:onClick #(comp/ptransact! this `[(add-item {:list-id ~id
                                                              :id      ~(comp/tempid)
                                                              :value   "A New Value"})
                                                   (set-overlay {:visible? false})
                                                   :overlay])} "Add item")))

(def ui-list (comp/factory ItemList {:keyfn :db/id}))

(defsc Overlay [this {:keys [:visible?] :as props}]
  {:query         [:db/id :visible?]
   :initial-state {:visible? false}}
  (dom/div {:onClick #(.stopPropagation %)
            :style   {:backgroundColor "black"
                      :display         (if visible? "block" "none")
                      :position        "absolute"
                      :opacity         "0.6"
                      :zIndex          "100"
                      :width           "600px"
                      :height          example-height}} ""))

(def ui-overlay (comp/factory Overlay {:keyfn :db/id}))

(defsc Root [this {:keys [overlay mutation-join-list]}]
  {:query         [{:overlay (comp/get-query Overlay)} {:mutation-join-list (comp/get-query ItemList)}]
   :initial-state {:overlay {} :mutation-join-list {}}}
  (dom/div {:style {:position "relative"}}
    (ui-overlay overlay)
    "Test"
    (when-not (empty? mutation-join-list)
      (ui-list mutation-join-list))))

(defn initialize "started callback" [app] (df/load app :mutation-join-list ItemList))
