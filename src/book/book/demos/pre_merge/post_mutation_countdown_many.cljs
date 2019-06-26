(ns book.demos.pre-merge.post-mutation-countdown-many
  (:require
    [com.fulcrologic.fulcro.data-fetch :as df]
    [book.demos.util :refer [now]]
    [com.fulcrologic.fulcro.mutations :as m]
    [com.fulcrologic.fulcro.dom :as dom]
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    [com.wsscode.pathom.connect :as pc]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; SERVER:
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def all-counters
  [{::counter-id 1 ::counter-label "A"}
   {::counter-id 2 ::counter-label "B"}
   {::counter-id 3 ::counter-label "C"}
   {::counter-id 4 ::counter-label "D"}])

(pc/defresolver counter-resolver [env _]
  {::pc/output [{::all-counters [::counter-id ::counter-label]}]}
  {::all-counters all-counters})

(def resolvers [counter-resolver])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; CLIENT:
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(m/defmutation initialize-counters [_]
  (action [{:keys [state]}]
    (swap! state
      (fn [state]
        (reduce
          (fn [state ref]
            (update-in state ref #(merge {:ui/count 5} %)))
          state
          (get state ::all-counters))))))

(defsc Countdown [this {::keys   [counter-label]
                        :ui/keys [count]}]
  {:ident [::counter-id ::counter-id]
   :query [::counter-id ::counter-label :ui/count]}
  (dom/div
    (dom/h4 counter-label)
    (let [done? (zero? count)]
      (dom/button {:disabled done?
                   :onClick  #(m/set-value! this :ui/count (dec count))}
        (if done? "Done!" (str count))))))

(def ui-countdown (comp/factory Countdown {:keyfn ::counter-id}))

(defsc Root [this {::keys [all-counters]}]
  {:initial-state (fn [_] {})
   :query         [{::all-counters (comp/get-query Countdown)}]}
  (dom/div
    (dom/h3 "Counters")
    (if (seq all-counters)
      (dom/div {:style {:display "flex" :alignItems "center" :justifyContent "space-between"}}
        (mapv ui-countdown all-counters))
      (dom/button {:onClick #(df/load! this ::all-counters Countdown
                               {:post-mutation `initialize-counters})}
        "Load many counters"))))
