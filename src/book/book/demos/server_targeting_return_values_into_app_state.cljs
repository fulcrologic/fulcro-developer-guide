(ns book.demos.server-targeting-return-values-into-app-state
  (:require
    [com.fulcrologic.fulcro.dom :as dom]
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    [com.fulcrologic.fulcro.dom :as dom]
    [com.fulcrologic.fulcro.mutations :as m :refer [defmutation]]
    [fulcro.server :as server]
    [com.fulcrologic.fulcro.data-fetch :as df]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; SERVER:
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(def ids (atom 1))

(server/defmutation trigger-error [_]
  (action [env]
    {:error "something bad"}))

(server/defmutation create-entity [{:keys [db/id]}]
  (action [env]
    (let [real-id (swap! ids inc)]
      {:db/id        real-id
       :entity/label (str "Entity " real-id)
       :tempids      {id real-id}})))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; CLIENT:
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(declare Item Entity)

(defmutation trigger-error
  "This mutation causes an unstructured error (just a map), but targets that value
   to the field `:error-message` on the component that invokes it."
  [_]
  (remote [{:keys [ast ref]}]
    (m/with-target ast (conj ref :error-message))))

(defmutation create-entity
  "This mutation simply creates a new entity, but targets it to a specific location
  (in this case the `:child` field of the invoking component)."
  [{:keys [where?] :as params}]
  (remote [{:keys [ast ref state]}]
    (let [path-to-target (conj ref :children)
          ; replacement cannot succeed if there is nothing present...turn those into appends
          no-items?      (empty? (get-in @state path-to-target))
          where?         (if (and no-items? (= :replace-first where?))
                           :append
                           where?)]
      (cond-> (-> ast
                ; always set what kind of thing is coming back
                (m/returning state Entity)
                ; strip the where?...it is for local use only (not server)
                (m/with-params (dissoc params :where?)))
        ; Add the targeting...based on where?
        (= :append where?) (m/with-target (df/append-to path-to-target)) ; where to put it
        (= :prepend where?) (m/with-target (df/prepend-to path-to-target))
        (= :replace-first where?) (m/with-target (df/replace-at (conj path-to-target 0)))))))

(defsc Entity [this {:keys [entity/label]}]
  {:ident [:entity/by-id :db/id]
   :query [:db/id :entity/label]}
  (dom/div label))

(def ui-entity (comp/factory Entity {:keyfn :db/id}))

(defsc Item [this {:keys [db/id error-message children]}]
  {:query         [:db/id :error-message {:children (comp/get-query Entity)}]
   :initial-state {:db/id :param/id :children []}
   :ident         [:item/by-id :db/id]}
  (dom/div {:style {:float  "left"
                    :width  "200px"
                    :margin "5px"
                    :border "1px solid black"}}
    (dom/h4 (str "Item " id))
    (when error-message
      (dom/div "The generated error was: " (pr-str error-message)))
    (dom/button {:onClick (fn [evt] (comp/transact! this `[(trigger-error {})]))} "Trigger Error")
    (dom/h6 "Children")
    (map ui-entity children)
    (dom/button {:onClick (fn [evt] (comp/transact! this `[(create-entity {:where? :prepend :db/id ~(comp/tempid)})]))} "Prepend one!")
    (dom/button {:onClick (fn [evt] (comp/transact! this `[(create-entity {:where? :append :db/id ~(comp/tempid)})]))} "Append one!")
    (dom/button {:onClick (fn [evt] (comp/transact! this `[(create-entity {:where? :replace-first :db/id ~(comp/tempid)})]))} "Replace first one!")))

(def ui-item (comp/factory Item {:keyfn :db/id}))

(defsc Root [this {:keys [root/items]}]
  {:query         [{:root/items (comp/get-query Item)}]
   :initial-state {:root/items [{:id 1} {:id 2} {:id 3}]}}
  (dom/div
    (mapv ui-item items)
    (dom/br {:style {:clear "both"}})))


