(ns book.demos.parallel-vs-sequential-loading
  (:require
    [com.fulcrologic.fulcro.data-fetch :as df]
    [com.fulcrologic.fulcro.dom :as dom]
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    [com.wsscode.pathom.connect :as pc]
    [taoensso.timbre :as log]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; SERVER:
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(pc/defresolver long-query-resolver [_ _]
  {::pc/output [:background/long-query]}
  {:background/long-query 42})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; CLIENT:
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defsc Child [this {:keys [id name background/long-query] :as props}]
  {:query (fn [] [:id :name :background/long-query [df/marker-table '_]])
   :ident [:background.child/by-id :id]}
  (let [status (get-in props [df/marker-table [:fetching id]])]
    (dom/div {:style {:display "inline" :float "left" :width "200px"}}
      (dom/button {:onClick #(df/load-field! this :background/long-query {:parallel true
                                                                          :marker   [:fetching id]})} "Load stuff parallel")
      (dom/button {:onClick #(df/load-field! this :background/long-query {:marker [:fetching id]})} "Load stuff sequential")
      (dom/div
        name
        (if (df/loading? status)
          (dom/span "Loading...")
          (dom/span long-query))))))

(def ui-child (comp/factory Child {:keyfn :id}))

(defsc Root [this {:keys [children] :as props}]
  ; cheating a little...raw props used for child, instead of embedding them there.
  {:initial-state (fn [params] {:children [{:id 1 :name "A"} {:id 2 :name "B"} {:id 3 :name "C"}]})
   :query         [{:children (comp/get-query Child)}]}
  (dom/div
    (mapv ui-child children)
    (dom/br {:style {:clear "both"}}) (dom/br)))

