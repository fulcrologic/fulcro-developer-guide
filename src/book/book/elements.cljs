(ns book.elements
  (:require
    [goog.object :as gobj]
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    [com.fulcrologic.fulcro.dom :as dom]))

(defn update-frame-content [this child]
  (let [frame-component (gobj/get this "frame-component")]
    (when frame-component
      (js/ReactDOM.render child frame-component))))

(defn start-frame [this]
  (let [frame-body (.-body (.-contentDocument (js/ReactDOM.findDOMNode this)))
        {:keys [child]} (comp/props this)
        e1         (.createElement js/document "div")]
    (when (= 0 (gobj/getValueByKeys frame-body #js ["children" "length"]))
      (.appendChild frame-body e1)
      (gobj/set this "frame-component" e1)
      (update-frame-content this child))))

(defsc IFrame [this props]
  {:componentDidMount  (fn [this] (start-frame this))
   :componentDidUpdate (fn [this _ _]
                         (let [child (:child (comp/props this))]
                           (update-frame-content this child)))}

  (dom/iframe
    (-> (comp/props this)
      (dissoc :child)
      (assoc :onLoad #(start-frame this))
      clj->js)))

(let [factory (comp/factory IFrame)]
  (defn ui-iframe [props child]
    (factory (assoc props :child child))))

