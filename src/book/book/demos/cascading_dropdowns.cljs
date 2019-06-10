(ns book.demos.cascading-dropdowns
  (:require
    [com.fulcrologic.fulcro.data-fetch :as df]
    [com.fulcrologic.fulcro.mutations :refer [defmutation]]
    [com.fulcrologic.fulcro.dom :as dom]
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    [book.elements :as ele]
    [taoensso.timbre :as log]
    [com.wsscode.pathom.connect :as pc]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Server
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn option [value text]
  {:text text :value value})

(pc/defresolver model-resolver [env _]
  {::pc/output [:models]}
  (let [make (-> env :ast :params :make)]
    {:models (case make
               :ford [(option :escort "Escort") (option :F-150 "F-150")]
               :honda [(option :civic "Civic") (option :accort "Accord")])}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Client
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn render-example
  "Wrap an example in an iframe so we can load external CSS without affecting the containing page."
  [width height & children]
  (ele/ui-iframe {:frameBorder 0 :height height :width width}
    (apply dom/div {:key "example-frame-key"}
      (dom/style ".boxed {border: 1px solid black}")
      (dom/link {:rel "stylesheet" :href "bootstrap-3.3.7/css/bootstrap.min.css"})
      children)))

(comment

  (defmutation show-list-loading
    "Change the items of the dropdown with the given ID to a single item that indicates Loading..."
    [{:keys [id]}]
    (action [{:keys [state]}]
      (swap! state assoc-in
        [:bootstrap.dropdown/by-id id :fulcro.ui.bootstrap3/items]
        [(assoc (bs/dropdown-item :loading "Loading...") :fulcro.ui.bootstrap3/disabled? true)])))

  (defsc Root [this {:keys [make-dropdown model-dropdown]}]
    {:initial-state (fn [params]
                      {:make-dropdown  (bs/dropdown :make "Make" [(bs/dropdown-item :ford "Ford")
                                                                  (bs/dropdown-item :honda "Honda")])
                       ; leave the model items empty
                       :model-dropdown (bs/dropdown :model "Model" [])})
     :query         [; initial state for two Bootstrap dropdowns
                     {:make-dropdown (comp/get-query bs/Dropdown)}
                     {:model-dropdown (comp/get-query bs/Dropdown)}]}
    (let [{:keys [:fulcro.ui.bootstrap3/items]} model-dropdown]
      (render-example "200px" "200px"
        (dom/div
          (bs/ui-dropdown make-dropdown
            :onSelect (fn [item]
                        ; Update the state of the model dropdown to show a loading indicator
                        (comp/transact! this `[(show-list-loading {:id :model})])
                        ; Issue the remote load. Note the use of DropdownItem as the query, so we get proper normalization
                        ; The targeting is used to make sure we hit the correct dropdown's items
                        (df/load this :models bs/DropdownItem {:target [:bootstrap.dropdown/by-id :model :fulcro.ui.bootstrap3/items]
                                                               ; don't overwrite state with loading markers...we're doing that manually to structure it specially
                                                               :marker false
                                                               ; A server parameter on the query
                                                               :params {:make item}}))
            :stateful? true)
          (bs/ui-dropdown model-dropdown
            :onSelect (fn [item] (log/info item))
            :stateful? true))))))
