(ns book.elements
  (:require
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    [com.fulcrologic.fulcro.dom :as dom]
    ["react-dom" :refer [createPortal]]
    [com.fulcrologic.fulcro.react.hooks :as hooks]))

(defsc IFrame [this props]
  {:use-hooks? true}
  (let [[^js cref setcref!] (hooks/use-state nil)
        mount-node (some-> cref (.-contentWindow) (.-document) (.-body))
        children [(:child props)]]
    (dom/iframe
      (-> (comp/props this)
        (dissoc :child)
        (assoc :ref setcref!)
        clj->js)
      (when mount-node
        (createPortal children mount-node)))))

(let [factory (comp/factory IFrame)]
  (defn ui-iframe [props child]
    (factory (assoc props :child child))))

