(ns book.demos.localized-dom
  (:require
    [com.fulcrologic.fulcro-css.css :as css]
    [com.fulcrologic.fulcro.mutations :refer [defmutation]]
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    [com.fulcrologic.fulcro-css.localized-dom :as dom]
    [com.fulcrologic.fulcro-css.css-injection :as cssi]))

(defonce theme-color (atom :blue))

(defsc Child [this {:keys [label invisible?]}]
  {:css           [[:.thing {:color @theme-color}]]
   :query         [:id :label :invisible?]
   :initial-state {:id :param/id :invisible? false :label :param/label}
   :ident         [:child/by-id :id]}
  (dom/div :.thing {:classes [(when invisible? :$hide)]} label))

(def ui-child (comp/factory Child))

(declare change-color)

(defmutation toggle-child [{:keys [id]}]
  (action [{:keys [state]}]
    (swap! state update-in [:child/by-id id :invisible?] not)))

(defsc Root [this {:keys [child]}]
  {:css           [[:$hide {:display :none}]]               ; a global CSS rule ".hide"
   :query         [{:child (comp/get-query Child)}]
   :initial-state {:child {:id 1 :label "Hello World"}}
   :css-include   [Child]}
  (dom/div
    (dom/button {:onClick (fn [e] (change-color "blue"))} "Use Blue Theme")
    (dom/button {:onClick (fn [e] (change-color "red"))} "Use Red Theme")
    (dom/button {:onClick (fn [e] (comp/transact! this `[(toggle-child {:id 1})]))} "Toggle visible")
    (ui-child child)))

(defn change-color [c]
  (reset! theme-color c)
  (cssi/upsert-css "demo-css-id" Root))

; Push the real CSS to the DOM via a component. One or more of these could be done to, for example,
; include CSS from different modules or libraries into different style elements.
(cssi/upsert-css "demo-css-id" Root)
