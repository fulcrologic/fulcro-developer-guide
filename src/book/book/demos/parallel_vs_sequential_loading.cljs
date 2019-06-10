(ns book.demos.parallel-vs-sequential-loading
  (:require
    [fulcro.server :refer [defquery-root defquery-entity defmutation]]
    [com.fulcrologic.fulcro.data-fetch :as df]
    [com.fulcrologic.fulcro.dom :as dom]
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; SERVER:
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defquery-entity :background.child/by-id
  (value [{:keys [parser query] :as env} id params]
    (when (= query [:background/long-query])
      (parser env query))))

(defquery-root :background/long-query
  (value [{:keys [ast query] :as env} params] 42))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; CLIENT:
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn render-result [v] (dom/span v))

(defsc Child [this {:keys [name name background/long-query]}]
  {:query [:id :name :background/long-query]
   :ident [:background.child/by-id :id]}
  (dom/div {:style {:display "inline" :float "left" :width "200px"}}
    (dom/button {:onClick #(df/load-field this :background/long-query :parallel true)} "Load stuff parallel")
    (dom/button {:onClick #(df/load-field this :background/long-query)} "Load stuff sequential")
    (dom/div
      name
      (df/lazily-loaded render-result long-query))))

(def ui-child (comp/factory Child {:keyfn :id}))

(defsc Root [this {:keys [children] :as props}]
  ; cheating a little...raw props used for child, instead of embedding them there.
  {:initial-state (fn [params] {:children [{:id 1 :name "A"} {:id 2 :name "B"} {:id 3 :name "C"}]})
   :query         [{:children (comp/get-query Child)}]}
  (dom/div
    (mapv ui-child children)
    (dom/br {:style {:clear "both"}}) (dom/br)))

