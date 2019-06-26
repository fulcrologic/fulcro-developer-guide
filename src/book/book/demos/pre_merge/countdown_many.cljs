(ns book.demos.pre-merge.countdown-many
  (:require
    [book.demos.util :refer [now]]
    [com.fulcrologic.fulcro.mutations :as m]
    [com.fulcrologic.fulcro.dom :as dom]
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    [com.fulcrologic.fulcro.data-fetch :as df]
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

(defsc Countdown [this {::keys   [counter-label]
                        :ui/keys [count]}]
  {:ident     [::counter-id ::counter-id]
   :query     [::counter-id ::counter-label :ui/count]
   :pre-merge (fn [{:keys [current-normalized data-tree]}]
                (merge
                  {:ui/count 5}
                  current-normalized
                  data-tree))}
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
      (dom/button {:onClick #(df/load! this ::all-counters Countdown)}
        "Load many counters"))))

(defn initialize
  "To be used in :started-callback to pre-load things."
  [app])
