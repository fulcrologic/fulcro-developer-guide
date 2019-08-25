(ns book.react-interop.react-motion-example
  (:require ["react-motion" :refer [Motion spring]]
            [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
            [com.fulcrologic.fulcro.dom :as dom]
            [com.fulcrologic.fulcro.mutations :as m]
            [com.fulcrologic.fulcro.algorithms.react-interop :as interop]))

(def ui-motion (interop/react-factory Motion))

(defsc Block [this {:block/keys [name]} {:keys [top]}]
  {:query         [:block/id :block/name]
   :ident         :block/id
   :initial-state {:block/id   1
                   :block/name "A Block"}}
  (dom/div {:style {:position "relative"
                    :top      top}}
    (dom/div
      (dom/label "Name?")
      (dom/input {:value    name
                  :onChange #(m/set-string! this :block/name :event %)}))))

(def ui-block (comp/factory Block {:keyfn :id}))

(defsc Demo [this {:keys [ui/slid? block]}]
  {:query         [:ui/slid? {:block (comp/get-query Block)}]
   :initial-state {:ui/slid? false :block {:id 1 :name "N"}}
   :ident         (fn [] [:control :demo])}
  (dom/div {:style {:overflow      "hidden"
                    :height        "150px"
                    :margin        "5px"
                    :padding       "5px"
                    :border        "1px solid black"
                    :border-radius "10px"}}
    (dom/button {:onClick (fn [] (m/toggle! this :ui/slid?))} "Toggle")
    (ui-motion {:style {"y" (spring (if slid? 175 0))}}
      (fn [p]
        (let [y (comp/isoget p "y")]
          ; The binding wrapper ensures that internal fulcro bindings are held within the lambda
          (comp/with-parent-context this
            (dom/div :.demo
              (ui-block (comp/computed block {:top y})))))))))

(def ui-demo (comp/factory Demo))

(defsc Root [this {:root/keys [demo] :as props}]
  {:query         [{:root/demo (comp/get-query Demo)}]
   :initial-state {:root/demo {}}}
  (ui-demo demo))

