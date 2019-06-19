(ns book.demos.loading-data-targeting-entities
  (:require
    [com.fulcrologic.fulcro.mutations :as m]
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    [com.fulcrologic.fulcro.dom :as dom]
    [com.fulcrologic.fulcro.data-fetch :as df]
    [com.fulcrologic.fulcro.components :as comp]
    [com.wsscode.pathom.connect :as pc]))

;; SERVER

(pc/defresolver random-person-resolver [env {:keys [id]}]
  {::pc/input [:person/id]
   ::pc/output [:person/name]}
  {:person/id id :person/name (str "Person " id)})

;; CLIENT

(defsc Person [this {:keys [person/name]}]
  {:query [:db/id :person/name]
   :ident [::person-by-id :db/id]}
  (dom/div (str "Hi, I'm " name)))

(def ui-person (comp/factory Person {:keyfn :db/id}))

(defsc Pane [this {:keys [db/id pane/person] :as props}]
  {:query         [:db/id {:pane/person (comp/get-query Person)}]
   :initial-state (fn [{:keys [id]}] {:db/id id :pane/person nil})
   :ident         [:pane/by-id :db/id]}

  (dom/div
    (dom/h4 (str "Pane " id))
    (if person
      (ui-person person)
      (dom/div "No person loaded..."))))

(def ui-pane (comp/factory Pane {:keyfn :db/id}))

(defsc Panel [this {:keys [panel/left-pane panel/right-pane]}]
  {:query         [{:panel/left-pane (comp/get-query Pane)}
                   {:panel/right-pane (comp/get-query Pane)}]
   :initial-state (fn [params] {:panel/left-pane  (comp/get-initial-state Pane {:id :left})
                                :panel/right-pane (comp/get-initial-state Pane {:id :right})})
   :ident         (fn [] [:PANEL :only-one])}
  (dom/div
    (ui-pane left-pane)
    (ui-pane right-pane)))

(def ui-panel (comp/factory Panel {:keyfn :db/id}))

(defn load-random-person [component where]
  (let [load-target  (case where
                       (:left :right) [:pane/by-id where :pane/person]
                       :both (df/multiple-targets
                               [:pane/by-id :left :pane/person]
                               [:pane/by-id :right :pane/person]))

        person-ident [::person-by-id (rand-int 100)]]
    (df/load! component person-ident Person {:target load-target :marker false})))

(defsc Root [this {:keys [root/panel] :as props}]
  {:query         [{:root/panel (comp/get-query Panel)}]
   :initial-state (fn [params] {:root/panel (comp/get-initial-state Panel {})})}
  (dom/div
    (ui-panel panel)
    (dom/button {:onClick #(load-random-person this :left)} "Load into Left")
    (dom/button {:onClick #(load-random-person this :right)} "Load into Right")
    (dom/button {:onClick #(load-random-person this :both)} "Load into Both")))


