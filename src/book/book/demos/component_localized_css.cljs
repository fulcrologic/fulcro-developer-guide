(ns book.demos.component-localized-css
  (:require
    [com.fulcrologic.fulcro-css.css :as css]
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    [com.fulcrologic.fulcro.dom :as dom]
    [com.fulcrologic.fulcro-css.localized-dom :as ldom]
    [com.fulcrologic.fulcro-css.css-injection :as cssi]
    [com.fulcrologic.fulcro.application :as app]))

(defonce theme-color (atom :blue))

(defsc Child [this {:keys [label]}]
  {; define local rules via garden. Defined names will be auto-localized
   :css (fn [] [[:.thing {:color @theme-color}]])}
  (let [{:keys [thing]} (css/get-classnames Child)]
    (dom/div
      (dom/h4 "Using css destructured value of CSS name")
      (dom/div {:className thing} label)
      (dom/h4 "Using automatically localized DOM in fulcro.client.localized-dom")
      (ldom/div :.thing label))))

(def ui-child (comp/factory Child))

(declare change-color)

(defsc Root [this {:keys [ui/react-key]}]
  {; Compose children with local reasoning. Dedupe is automatic if two UI paths cause re-inclusion.
   :css-include [Child]}
  (let [app (comp/any->app this)]
    (dom/div
      (dom/button {:onClick (fn [e]
                              ; change the atom, and re-upsert the CSS. Look at the elements in your dev console.
                              ; Figwheel and Closure push SCRIPT tags too, so it may be hard to find on
                              ; initial load. You might try clicking one of these
                              ; to make it easier to find (the STYLE will pop to the bottom).
                              (change-color "blue"))} "Use Blue Theme")
      (dom/button {:onClick (fn [e] (change-color "red"))} "Use Red Theme")
      (ui-child {:label "Hello World"}))))

(defn change-color [c]
  (reset! theme-color c)
  (cssi/upsert-css "demo-css-id" {:component     Root
                                  :auto-include? false}))

; Push the real CSS to the DOM via a component. One or more of these could be done to, for example,
; include CSS from different modules or libraries into different style elements.
(cssi/upsert-css "demo-css-id" {:component     Root
                                :auto-include? false})
