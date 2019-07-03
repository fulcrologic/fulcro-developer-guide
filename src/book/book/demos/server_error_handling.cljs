(ns book.demos.server-error-handling
  (:require
    [com.fulcrologic.fulcro.data-fetch :as df]
    [com.fulcrologic.fulcro.mutations :as m :refer [defmutation]]
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    [com.fulcrologic.fulcro.dom :as dom]
    [taoensso.timbre :as log]
    [com.wsscode.pathom.connect :as pc]
    [com.wsscode.pathom.core :as p]
    [com.fulcrologic.fulcro.application :as app]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; SERVER:
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(pc/defmutation server-error-mutation [env params]
  {::pc/sym `error-mutation}
  ;; Throw a mutation error for the client to handle
  (throw (ex-info "Server error" {:type :com.fulcrologic.fulcro.components/abort :status 401 :body "Unauthorized User"})))

(pc/defresolver child-resolver [env input]
  {::pc/output [:fulcro/read-error]}
  (throw (ex-info "other read error" {:status 403 :body "Not allowed."})))

(def resolvers [server-error-mutation child-resolver])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; CLIENT:
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defmutation disable-button [{:keys [::comp/ref] :as params}]
  (action [{:keys [state]}]
    (js/alert "Mutation error -- disabling button due to error from mutation invoked at " ref)
    (swap! state assoc-in [:error.child/by-id :singleton :ui/button-disabled] true)))

(defmutation log-read-error [{:keys [::comp/ref] :as params}]
  (action [{:keys [state]}]
    (js/alert "Read failed.")))

;; an :error key is injected into the fallback mutation's params argument
(defmutation error-mutation [params]
  (ok-action [env] (js/console.log :ok))
  (error-action [{:keys [app ref]}]
    ;; in order for this to be called, you have to set up your error detection properly on the app.
    (comp/transact! app [(disable-button {::comp/ref ref})]))
  (remote [env] true))

(defsc Child [this {:keys [fulcro/server-error ui/button-disabled]}]
  ;; you can query for the server-error using a link from any component that composes to root
  {:initial-state (fn [p] {})
   :query         (fn [] [[:fulcro/server-error '_] :ui/button-disabled :fulcro/read-error])
   :ident         (fn [] [:error.child/by-id :singleton])}  ; lambda so we get a *literal* ident
  (dom/div
    ;; declare a tx/fallback in the same transact call as the mutation
    ;; if the mutation fails, the fallback will be called
    (dom/button {:onClick #(df/load! this :fulcro/read-error nil {:fallback `log-read-error})}
      "Click me to try a read with a fallback (logs to console)")
    (dom/button {:onClick  #(comp/transact! this `[(error-mutation {})])
                 :disabled button-disabled}
      "Click me for error (disables on error)!")
    (dom/div "Server error (root level): " (str server-error))))

(def ui-child (comp/factory Child))

(defsc Root [this {:keys [child]}]
  {:initial-state (fn [params] {:child (comp/get-initial-state Child {})})
   :query         [{:child (comp/get-query Child)}]}
  (dom/div (ui-child child)))

(defn contains-error?
  "Check to see if the response contains Pathom error indicators"
  [body]
  (when (map? body)
    (let [values (vals body)]
      (reduce
        (fn [error? v]
          (if (or
                (and (map? v) (contains? (set (keys v)) ::p/reader-error))
                (= v ::p/reader-error))
            (reduced true)
            error?))
        false
        values))))

(def SPA (app/fulcro-app {:remote-error? (fn [{:keys [body] :as result}]
                                           (or
                                             (app/default-remote-error? result)
                                             (contains-error? body)))}))
