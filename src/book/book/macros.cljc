(ns book.macros
  #?(:cljs (:require-macros book.macros))
  (:require
    [com.fulcrologic.fulcro.application :as app]
    [com.fulcrologic.fulcro-css.css :as css]
    #?@(:cljs [[goog.object :as obj]
               [devcards.util.edn-renderer :as edn]])
    #?(:cljs [com.fulcrologic.fulcro.dom :as dom]
       :clj  [com.fulcrologic.fulcro.dom-server :as dom])
    [com.fulcrologic.fulcro.mutations :as m :refer [defmutation]]
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    [taoensso.timbre :as log]))

#?(:clj (def clj->js identity))

(defmutation update-db-view [{:keys [value]}]
  (action [{:keys [state]}]
    (swap! state assoc :watched-state value))
  (refresh [env] [:watched-state]))

#?(:cljs
   (defn watch-state [this example-app]
     (let [target-app-state (some-> example-app ::app/state-atom)]
       (when target-app-state
         (comp/transact! this `[(update-db-view {:value ~(deref target-app-state)})])
         (add-watch target-app-state
           :example-watch (fn []
                            (comp/transact! this `[(update-db-view {:value ~(deref target-app-state)})])))))))

(defsc AppHolder [this _ _ {:keys [app-holder]}]
  {:shouldComponentUpdate (fn [_ _] false)
   :css                   [[:.app-holder {:border  "2px solid grey"
                                          :padding "10px"}]]
   :componentDidCatch     (fn [err info] (log/error "App holder failed to start." err info))
   :componentDidMount     (fn []
                            #?(:cljs (let [{:keys [app root]} (meta (comp/props this))]
                                       (if (and app root)
                                         ;; necessary so we don't close over the outer app's reconciler when rendering
                                         (js/setTimeout #(if-let [target-div (obj/get this "appdiv")]
                                                           (let [app (app/mount! app root target-div)]
                                                             (comp/set-state! this {:app app})
                                                             (watch-state this app))
                                                           (log/fatal "App holder: Target div not found."))
                                           10)
                                         (log/fatal "App holder: Not given an app or root" :app app :root root)))))}
  #?(:clj  (dom/div nil "")
     :cljs (dom/div {:className app-holder :ref (fn [r] (obj/set this "appdiv" r))} "")))

(def ui-app-holder (comp/factory AppHolder))

(defsc EDN [this {:keys [ui/open?] :as props} {:keys [edn]} {:keys [toggle-button db-block]}]
  {:initial-state {:ui/open? false}
   :query         [:ui/open?]
   :css           [[:.db-block {:padding "5px"}]
                   [:.toggle-button {:font-size "8pt"
                                     :margin    "5px"}]]
   :ident         (fn [] [:widgets/by-id :edn-renderer])}
  #?(:cljs
     (dom/div {:className "example-edn"}
       (dom/button {:className toggle-button :onClick (fn [] (m/toggle! this :ui/open?))} "Toggle DB View")
       (dom/div {:className db-block :style {:display (if open? "block" "none")}}
         (edn/html-edn edn)))))

(def ui-edn (comp/factory EDN))

(defsc ExampleRoot [this {:keys [edn-tool watched-state title example-app] :as props} _ {:keys [example-title]}]
  {:query         [{:edn-tool (comp/get-query EDN)}
                   :watched-state
                   :title
                   :example-app]
   :css           [[:.example-title {:margin                  "0"
                                     :padding                 "5px"
                                     :border-top-left-radius  "8px"
                                     :border-top-right-radius "8px"
                                     :width                   "100%"
                                     :color                   "white"
                                     :background-color        "rgb(70, 148, 70)"}]]
   :css-include   [EDN AppHolder]
   :initial-state {:edn-tool {}}}
  (let [has-title? (not= "" title)]
    (dom/div nil
      (when has-title? (dom/h4 {:className example-title} title))
      (ui-app-holder example-app)
      (when has-title? (ui-edn (comp/computed edn-tool {:edn watched-state}))))))

(defn new-example [{:keys [title example-app root-class]}]
  (app/fulcro-app {:initial-state (merge (comp/get-initial-state ExampleRoot {})
                                    {:example-app (with-meta {} {:app example-app :root root-class})
                                     :title       title})}))

(defmacro defexample [title root-class id & args]
  (let [app         (with-meta (symbol (str "fulcroapp-" id)) {:extern true})
        example-app (with-meta (symbol (str "example-container-" id)) {:extern true})]
    `(do
       (defonce ~app (app/fulcro-app (into {:id ~(name app)} ~@args)))
       (defonce ~example-app (book.macros/new-example {:title ~title :example-app ~app :root-class ~root-class}))
       (app/mount! ~example-app ExampleRoot ~id))))

(defmacro deftool [root-class id & args]
  (let [app (symbol (str "fulcroapp-" id))]
    `(do
       (defonce ~app (app/fulcro-app (into {} ~@args)))
       (app/mount! ~app ~root-class ~id))))
