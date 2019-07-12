(ns book.server.ui-blocking-example
  (:require
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    [com.fulcrologic.fulcro.dom :as dom]
    [com.fulcrologic.fulcro.mutations :as m :refer [defmutation]]
    [com.wsscode.pathom.connect :as pc]
    [taoensso.timbre :as log]))

;; SERVER

(pc/defmutation submit-form-mutation [env params]
  {::pc/sym `submit-form}
  (log/info "Server got " params)
  (if (> 0.5 (rand))
    {:message "Everything went swell!"
     :ok?     true}
    {:message "There was an error!"
     :ok?     false}))

;; CLIENT

(defsc BlockingOverlay [this {:keys [ui/active? ui/message]}]
  {:query         [:ui/active? :ui/message]
   :initial-state {:ui/active? false :ui/message "Please wait..."}}
  (dom/div {:style {:position        :absolute
                    :display         (if active? "block" "none")
                    :zIndex          65000
                    :width           "400px"
                    :height          "100px"
                    :backgroundColor "rgba(0,0,0,0.5)"}}
    (dom/div {:style {:position  :relative
                      :top       "40px"
                      :color     "white"
                      :textAlign "center"}} message)))

(def ui-overlay (comp/factory BlockingOverlay))

(defn set-overlay-visible* [state tf] (assoc-in state [:overlay :ui/active?] tf))
(defn set-overlay-message* [state message] (assoc-in state [:overlay :ui/message] message))

(defmutation submit-form [params]
  (action [{:keys [state]}]
    (swap! state (fn [s]
                   (-> s
                     (set-overlay-message* "Working...")
                     (set-overlay-visible* true)))))
  (ok-action [{:keys [app state result]}]
    (log/info "Result:" result)
    (let [mutation-result (-> result :body (get `submit-form))
          {:keys [message ok?]} mutation-result]
      (if ok?
        (swap! state set-overlay-visible* false)
        (do
          (swap! state set-overlay-message* (str message "   Retrying submission in 1s."))
          ;; could use setTimeout or immediately do it
          (js/setTimeout
            #(comp/transact! app [(submit-form params)])
            1000)))))
  (remote [_] true))

(defsc Root [this {:keys [ui/name overlay]}]
  {:query         [:ui/name {:overlay (comp/get-query BlockingOverlay)}]
   :initial-state {:overlay {} :ui/name "Alicia"}}
  (dom/div {:style {:width "400px" :height "100px"}}
    (ui-overlay overlay)
    (dom/p "Name: " (dom/input {:value name}))
    (dom/button {:onClick #(comp/transact! this [(submit-form {:made-up-data 42})])}
      "Submit")))

