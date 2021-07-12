(ns book.raw.raw-uism
  (:require
    [com.fulcrologic.fulcro.algorithms.data-targeting :as dt]
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    [com.fulcrologic.fulcro.dom :as dom :refer [div p input button h2 label]]
    [com.fulcrologic.fulcro.dom.events :as evt]
    [com.fulcrologic.fulcro.mutations :as m]
    [com.fulcrologic.fulcro.raw.components :as rc]
    [com.fulcrologic.fulcro.react.hooks :as hooks]
    [com.fulcrologic.fulcro.ui-state-machines :as uism]
    [com.wsscode.pathom.connect :as pc]
    [taoensso.timbre :as log]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Mock Server and database, in Fulcro client format for ease of use in demo
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defonce pretend-server-database
  (atom
    {:account/id {1000 {:account/id       1000
                        :account/email    "bob@example.com"
                        :account/password "letmein"}}}))

;; For the UISM DEMO
(defonce session-id (atom 1000))                            ; pretend like we have server state to remember client

(pc/defresolver account-resolver [_ {:account/keys [id]}]
  {::pc/input  #{:account/id}
   ::pc/output [:account/email]}
  (select-keys (get-in @pretend-server-database [:account/id id] {}) [:account/email]))

(pc/defresolver session-resolver [_ {:account/keys [id]}]
  {::pc/output [{:current-session [:account/id]}]}
  (if @session-id
    {:current-session {:account/id @session-id}}
    {:current-session {:account/id :none}}))

(pc/defmutation server-login [_ {:keys [email password]}]
  {::pc/sym    `login
   ::pc/output [:account/id]}
  (let [accounts (vals (get @pretend-server-database :account/id))
        account  (first
                   (filter
                     (fn [a] (and (= password (:account/password a)) (= email (:account/email a))))
                     accounts))]
    (when (log/spy :info "Found account" account)
      (reset! session-id (:account/id account))
      account)))

(pc/defmutation server-logout [_ _]
  {::pc/sym `logout}
  (reset! session-id nil))

(def resolvers [account-resolver session-resolver server-login server-logout])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Client.
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def global-events {:event/unmounted {::uism/handler (fn [env] env)}})

(uism/defstatemachine session-machine
  {::uism/actor-names
   #{:actor/login-form :actor/current-account}

   ::uism/aliases
   {:email    [:actor/login-form :email]
    :password [:actor/login-form :password]
    :failed?  [:actor/login-form :failed?]
    :name     [:actor/current-account :account/email]}

   ::uism/states
   {:initial
    {::uism/handler (fn [env]
                      (-> env
                        (uism/apply-action assoc-in [:account/id :none] {:account/id :none})
                        (uism/apply-action assoc-in [:component/id ::LoginForm]
                          {:component/id ::LoginForm :email "" :password "" :failed? false})
                        (uism/load :current-session :actor/current-account {::uism/ok-event    :event/done
                                                                            ::uism/error-event :event/done})
                        (uism/activate :state/checking-session)))}

    :state/checking-session
    {::uism/events
     (merge global-events
       {:event/done       {::uism/handler
                           (fn [{::uism/keys [state-map] :as env}]
                             (let [id (some-> state-map :current-session second)]
                               (cond-> env
                                 (pos-int? id) (->
                                                 (uism/reset-actor-ident :actor/current-account [:account/id id])
                                                 (uism/activate :state/logged-in))
                                 (not (pos-int? id)) (uism/activate :state/gathering-credentials))))}
        :event/post-login {::uism/handler
                           (fn [{::uism/keys [state-map] :as env}]
                             (let [session-ident (get state-map :current-session)
                                   Session       (uism/actor-class env :actor/current-account)
                                   logged-in?    (pos-int? (second session-ident))]
                               (if logged-in?
                                 (-> env
                                   (uism/reset-actor-ident :actor/current-account (uism/with-actor-class session-ident Session))
                                   (uism/activate :state/logged-in))
                                 (-> env
                                   (uism/assoc-aliased :failed? true)
                                   (uism/activate :state/gathering-credentials)))))}})}

    :state/gathering-credentials
    {::uism/events
     (merge global-events
       {:event/login {::uism/handler
                      (fn [env]
                        (-> env
                          (uism/assoc-aliased :failed? false)
                          (uism/trigger-remote-mutation :actor/login-form `login {:email             (uism/alias-value env :email)
                                                                                  :password          (uism/alias-value env :password)
                                                                                  ::m/returning      (uism/actor-class env :actor/current-account)
                                                                                  ::dt/target        [:current-session]
                                                                                  ::uism/ok-event    :event/post-login
                                                                                  ::uism/error-event :event/post-login})
                          (uism/activate :state/checking-session)))}})}

    :state/logged-in
    {::uism/events
     (merge global-events
       {:event/logout {::uism/handler
                       (fn [env]
                         (let [Session (uism/actor-class env :actor/current-account)]
                           (-> env
                             (uism/apply-action assoc :account/id {:none {}})
                             (uism/assoc-aliased :email "" :password "" :failed? false)
                             (uism/reset-actor-ident :actor/current-account (uism/with-actor-class [:account/id :none] Session))
                             (uism/trigger-remote-mutation :actor/current-account `logout {})
                             (uism/activate :state/gathering-credentials))))}})}}})

(def LoginForm (rc/nc [:component/id :email :password :failed?]
                 {:componentName ::LoginForm
                  :ident         (fn [] [:component/id ::LoginForm])}))

(def Session (rc/nc [:account/id :account/email] {:componentName ::Session}))

(defsc MainScreen [this props]
  {:use-hooks? true}
  (let [app       (comp/any->app this)
        {:actor/keys [login-form current-account]
         :keys       [active-state] :as sm} (hooks/use-uism app session-machine :sessions
                                              {::uism/actors {:actor/login-form      LoginForm
                                                              :actor/current-account (uism/with-actor-class [:account/id :none] Session)}})
        {:keys [email password failed?]} login-form
        checking? (= :state/checking-session active-state)]
    (if (= :state/logged-in active-state)
      (div :.ui.segment
        (dom/p {} (str "Hi," (:account/email current-account)))
        (button :.ui.red.button {:onClick #(uism/trigger! app :sessions :event/logout)} "Logout"))
      (div :.ui.segment
        (dom/h5 :.ui.header "Username is bob@example.com, password is letmein")
        (div :.ui.form {:classes [(when failed? "error")
                                  (when checking? "loading")]}
          (div :.field
            (label "Email")
            (input {:value    (or email "")
                    :onChange (fn [evt] (m/raw-set-value! app login-form :email (evt/target-value evt)))}))
          (div :.field
            (label "Password")
            (input {:type     "password"
                    :onChange (fn [evt] (m/raw-set-value! app login-form :password (evt/target-value evt)))
                    :value    (or password "")}))
          (div :.ui.error.message
            "Invalid credentials. Please try again.")
          (div :.field
            (button :.ui.primary.button {:onClick (fn [] (uism/trigger! app :sessions :event/login {}))} "Login")))))))

(def ui-main-screen (comp/factory MainScreen))

(defsc Root [this _]
  {}
  ;; NOTE: Hooks and Root without a query don't mix well, so we push the example down one level.
  (ui-main-screen {}))
