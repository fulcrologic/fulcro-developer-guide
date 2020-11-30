(ns book.demos.cascading-dropdowns
  (:require
    [com.fulcrologic.semantic-ui.modules.dropdown.ui-dropdown :as dropdown]
    [com.fulcrologic.fulcro.data-fetch :as df]
    [com.fulcrologic.fulcro.mutations :refer [defmutation]]
    [com.fulcrologic.fulcro.dom :as dom]
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    [book.elements :as ele]
    [taoensso.timbre :as log]
    [com.wsscode.pathom.connect :as pc]
    [com.fulcrologic.fulcro.mutations :as m]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Server
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn option [text]
  {:text text :value text})

(pc/defresolver model-resolver [env _]
  {::pc/output [::models]}
  (let [{:car/keys [make]} (-> env :ast :params)]
    {::models
     (case make
       "Ford" [(option "Escort") (option "F-150")]
       "Honda" [(option "Civic") (option "Accord")]
       [])}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Client
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn render-example
  "Wrap an example in an iframe so we can load external CSS without affecting the containing page."
  [width height & children]
  (ele/ui-iframe {:frameBorder 0 :height height :width width}
    (apply dom/div {:key "example-frame-key"}
      (dom/style ".boxed {border: 1px solid black}")
      (dom/link {:rel "stylesheet" :href "https://cdnjs.cloudflare.com/ajax/libs/semantic-ui/2.4.1/semantic.min.css"})
      children)))

(defsc Car [this {:car/keys [id make model]
                  :ui/keys  [car-model-options] :as props}]
  {:query         [:car/id :car/make :car/model
                   :ui/car-model-options
                   ;; Link queries goes to root of database. Here we're accessing the named load markers
                   [df/marker-table '_]]
   :initial-state {:car/id 1}
   :ident         :car/id}
  (let [models-loading? (df/loading? (get props [df/marker-table ::dropdown-loading]))]
    (dom/div :.ui.container.form
      (dom/h4 "Car")
      (dom/div :.ui.field
        (dom/label {:htmlFor "carmake"} "Make")
        (dropdown/ui-dropdown
          {:value       make
           :name        "carmake"
           :button      true
           :placeholder "Select"
           :options     [{:text "Ford" :value "Ford"}
                         {:text "Honda" :value "Honda"}]
           :onChange    (fn [_ item] (let [v (.-value item)]
                                       (m/set-string! this :car/make :value v)
                                       (m/set-string! this :car/model :value "")
                                       (df/load this ::models nil {; custom marker so we can show that the dropdown is busy
                                                                   :marker ::dropdown-loading
                                                                   ; A server parameter on the query
                                                                   :params {:car/make v}
                                                                   :target [:car/id id :ui/car-model-options]})))}))
      (dom/div :.ui.field
        (dom/label "Model")
        (dropdown/ui-dropdown
          {:onSelect    (fn [item] (log/info item))
           :button      true
           :placeholder "Select"
           :options     (or car-model-options [{:text "Select Make" :value ""}])
           :value       (or model "")
           :onChange    (fn [_ item]
                          (m/set-string! this :car/model :value (.-value item)))
           :loading     models-loading?})))))

(def ui-car (comp/factory Car {:keyfn :car/id}))

(defsc Root [this {:keys [form]}]
  {:initial-state {:form {}}
   :query         [{:form (comp/get-query Car)}]}
  (render-example "400px" "400px"
    (ui-car form)))
