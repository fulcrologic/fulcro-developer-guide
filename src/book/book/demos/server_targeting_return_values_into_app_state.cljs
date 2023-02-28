(ns book.demos.server-targeting-return-values-into-app-state
  (:require
    [com.fulcrologic.fulcro.dom :as dom]
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    [com.fulcrologic.fulcro.dom :as dom]
    [com.fulcrologic.fulcro.algorithms.data-targeting :as targeting]
    [com.fulcrologic.fulcro.mutations :as m :refer [defmutation]]
    [com.fulcrologic.fulcro.data-fetch :as df]
    [com.wsscode.pathom.connect :as pc]
    [com.wsscode.pathom.core :as p]
    [com.fulcrologic.fulcro.algorithms.tempid :as tempid]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; SERVER:
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(def ids (atom 1))
(pc/defmutation server-error-mutation [env params]
  {::pc/sym `trigger-error}
  ;; Throw a mutation error for the client to handle
  (throw (ex-info "Mutation error" {:random-reason (rand-int 100)})))

(pc/defmutation server-create-entity [env {:keys [db/id]}]
  {::pc/sym `create-entity}
  (let [real-id (swap! ids inc)]
    {:db/id        real-id
     :entity/label (str "Entity " real-id)
     :tempids      {id real-id}}))

(def resolvers [server-error-mutation server-create-entity])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; CLIENT:
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(declare Item Entity)

(defmutation trigger-error
  "This mutation causes an unstructured error (just a map), but targets that value
   to the field `:error-message` on the component that invokes it."
  [_]
  (remote [{:keys [ref] :as env}]
    (m/with-target env (conj ref :error-message))))

(defmutation create-entity
  "This mutation simply creates a new entity, but targets it to a specific location
  (in this case the `:child` field of the invoking component)."
  [{:keys [where?] :as params}]
  (remote [{:keys [ast ref state] :as env}]
    (let [path-to-target (conj ref :children)
          ; replacement cannot succeed if there is nothing present...turn those into appends
          no-items?      (empty? (get-in @state path-to-target))
          where?         (if (and no-items? (= :replace-first where?))
                           :append
                           where?)]
      (cond-> (-> env
                ; always set what kind of thing is coming back
                (m/returning Entity)
                ; strip the where?...it is for local use only (not server)
                (m/with-params (dissoc params :where?)))
        ; Add the targeting...based on where?
        (= :append where?) (m/with-target (targeting/append-to path-to-target)) ; where to put it
        (= :prepend where?) (m/with-target (targeting/prepend-to path-to-target))
        (= :replace-first where?) (m/with-target (targeting/replace-at (conj path-to-target 0)))))))

(defsc Entity [this {:keys [db/id entity/label]}]
  {:ident [:entity/by-id :db/id]
   :query [:db/id :entity/label]}
  (dom/li {:key id} label))

(def ui-entity (comp/factory Entity {:keyfn :db/id}))

(defsc Item [this {:keys [db/id error-message children]}]
  {:query         [:db/id :error-message {:children (comp/get-query Entity)}]
   :initial-state {:db/id :param/id :children []}
   :ident         [:item/by-id :db/id]}
  (dom/div :.ui.container.segment #_{:style {:float  "left"
                                             :width  "200px"
                                             :margin "5px"
                                             :border "1px solid black"}}
    (dom/h4 (str "Item " id))
    (dom/button {:onClick (fn [evt] (comp/transact! this [(trigger-error {})]))} "Trigger Error")
    (dom/button {:onClick (fn [evt] (comp/transact! this [(create-entity {:where? :prepend :db/id (tempid/tempid)})]))} "Prepend one!")
    (dom/button {:onClick (fn [evt] (comp/transact! this [(create-entity {:where? :append :db/id (tempid/tempid)})]))} "Append one!")
    (dom/button {:onClick (fn [evt] (comp/transact! this [(create-entity {:where? :replace-first :db/id (tempid/tempid)})]))} "Replace first one!")
    (when error-message
      (dom/div
        (dom/p "Error:")
        (dom/pre (pr-str error-message))))
    (dom/h6 "Children")
    (dom/ul
      (mapv ui-entity children))))

(def ui-item (comp/factory Item {:keyfn :db/id}))

(defsc Root [this {:keys [root/items]}]
  {:query         [{:root/items (comp/get-query Item)}]
   :initial-state {:root/items [{:id 1} {:id 2} {:id 3}]}}
  (dom/div
    (mapv ui-item items)
    (dom/br {:style {:clear "both"}})))


