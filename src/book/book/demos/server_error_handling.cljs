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
  (throw (ex-info "Mutation error" {})))

(pc/defresolver child-resolver [env input]
  {::pc/output [:fulcro/read-error]}
  (throw (ex-info "read error" {})))

(def resolvers [server-error-mutation child-resolver])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; CLIENT:
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; Mutation used as a fallback for load error: In this case the `env` from the load result *is* the params to this mutation
(defmutation read-error [params]
  (action [env]
    (js/alert "There was a read error")
    (log/info "Result from server:" (:result params))
    (log/info "Original load params:" (:load-params params))))

;; an :error key is injected into the fallback mutation's params argument
(defmutation error-mutation [params]
  (ok-action [env] (log/info "Optimistic action ran ok"))
  ;; Error action is only called if `:remote-error?` for the application is defined to consider the response an error.
  (error-action [{:keys [app ref result]}]
    (js/alert "Mutation error")
    (log/info "Result " result))
  (remote [env] true))

(defsc Child [this props]
  {:initial-state {}
   :query         ['*]
   :ident         (fn [] [:error.child/by-id :singleton])}
  (dom/div
    ;; declare a tx/fallback in the same transact call as the mutation
    ;; if the mutation fails, the fallback will be called
    (dom/button {:onClick #(df/load! this :fulcro/read-error nil {:fallback `read-error})}
      "Failing read with a fallback")
    (dom/button {:onClick #(comp/transact! this [(error-mutation {})])} "Failing mutation")
    ))

(def ui-child (comp/factory Child))

(defsc Root [this {:keys [child]}]
  {:initial-state (fn [params] {:child (comp/get-initial-state Child {})})
   :query         [{:child (comp/get-query Child)}]}
  (dom/div (ui-child child)))

(defn contains-error?
  "Check to see if the response contains Pathom error indicators."
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
