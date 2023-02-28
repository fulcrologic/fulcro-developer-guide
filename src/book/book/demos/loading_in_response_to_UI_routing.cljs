(ns book.demos.loading-in-response-to-UI-routing
  (:require
    [com.fulcrologic.fulcro.routing.legacy-ui-routers :as r]
    [com.fulcrologic.fulcro.mutations :as m]
    [com.fulcrologic.fulcro.dom :as dom]
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    [com.fulcrologic.fulcro.data-fetch :as df]
    [com.wsscode.pathom.connect :as pc]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; SERVER:
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(pc/defresolver all-settings-resolver [env input]
  {::pc/output [{::all-settings [:id :value]}]}
  {::all-settings [{:id 1 :value "Gorgon"}
                   {:id 2 :value "Thraser"}
                   {:id 3 :value "Under"}]})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; CLIENT:
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defsc SomeSetting [this {:keys [id value]}]
  {:query [:ui/fetch-state :id :value]
   :ident [:setting/by-id :id]}
  (dom/p nil "Setting " id " from server has value: " value))

(def ui-setting (comp/factory SomeSetting {:keyfn :id}))

(defsc SettingsTab [this {:keys [settings-content settings]}]
  {:initial-state {:kind             :settings
                   :settings-content "Settings Tab"
                   :settings         []}
   ; This query uses a "link"...a special ident with '_ as the ID. This indicates the item is at the database
   ; root, not inside of the "settings" database object. This is not needed as a matter of course...it is only used
   ; for convenience (since it is trivial to load something into the root of the database)
   :query         [:kind :settings-content {:settings (comp/get-query SomeSetting)}]}
  (dom/div nil
    settings-content
    (if (seq settings)
      (mapv ui-setting settings)
      (dom/div "No settings."))))

(defsc MainTab [this {:keys [main-content]}]
  {:initial-state {:kind :main :main-content "Main Tab"}
   :query         [:kind :main-content]}
  (dom/div nil main-content))

(r/defsc-router UITabs [this props]
  {:router-id      :ui-router
   :ident          (fn [] [(:kind props) :tab])
   :default-route  MainTab
   :router-targets {:main     MainTab
                    :settings SettingsTab}})

(def ui-tabs (comp/factory UITabs))

(m/defmutation choose-tab [{:keys [tab]}]
  (action [{:keys [state]}] (swap! state r/set-route* :ui-router [tab :tab])))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; LAZY LOADING TAB CONTENT
;; This is the shape of what to do. We define a method that can examine the
;; state to decide if we want to trigger a load. Then we define a mutation
;; that the UI can call during transact (see the transact! call for Settings on Root in ui.cljs).
;; The mutation itself (app/lazy-load-tab) below uses a data-fetch helper function to
;; set :remote to the right thing, and can then give one or more load-data-action's to
;; indicate what should actually be retrieved. The server implementation is trivial in
;; this case. See api.clj.

;; When to consider the data missing? Check the state and find out.
(defn missing-tab? [state tab]
  (let [settings (-> @state :settings :tab :settings)]
    (or (not (vector? settings))
      (and (vector? settings) (empty? settings)))))

(m/defmutation lazy-load-tab [{:keys [tab]}]
  (action [{:keys [app state] :as env}]
    ; Specify what you want to load as one or more calls to load-action (each call adds an item to load):
    (when (missing-tab? state tab)
      (df/load! app ::all-settings SomeSetting {:target [:settings :tab :settings]}))))

(defsc Root [this {:keys [current-tab] :as props}]
  ; Construction MUST compose to root, just like the query. The resulting tree will automatically be normalized into the
  ; app state graph database.
  {:initial-state (fn [params] {:current-tab (comp/get-initial-state UITabs nil)})
   :query         [{:current-tab (comp/get-query UITabs)}]}
  (dom/div
    ; The selection of tabs can be rendered in a child, but the transact! must be done from the parent (to
    ; ensure proper re-render of the tab body). See comp/computed for passing callbacks.
    (dom/button {:onClick #(comp/transact! this [(choose-tab {:tab :main})])} "Main")
    (dom/button {:onClick #(comp/transact! this [(choose-tab {:tab :settings})
                                                 ; extra mutation: sample of what you would do to lazy load the tab content
                                                 (lazy-load-tab {:tab :settings})])} "Settings")
    (ui-tabs current-tab)))
