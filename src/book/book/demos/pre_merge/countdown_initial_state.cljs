(ns book.demos.pre-merge.countdown-initial-state
  (:require
    [com.fulcrologic.fulcro.data-fetch :as df]
    [book.demos.util :refer [now]]
    [com.fulcrologic.fulcro.mutations :as m]
    [com.fulcrologic.fulcro.dom :as dom]
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    [com.wsscode.pathom.connect :as pc]
    [com.fulcrologic.fulcro.algorithms.merge :as merge]
    [com.fulcrologic.fulcro.algorithms.tempid :as tempid]
    [taoensso.timbre :as log]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; SERVER:
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def all-counters
  [{::counter-id 1 ::counter-label "A"}
   {::counter-id 2 ::counter-label "B" ::counter-initial 10}
   {::counter-id 3 ::counter-label "C" ::counter-initial 2}
   {::counter-id 4 ::counter-label "D"}])

(pc/defresolver counter-resolver [env _]
  {::pc/output [{::all-counters [::counter-id ::counter-label]}]}
  {::all-counters all-counters})

(def resolvers [counter-resolver])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; CLIENT:
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def default-count 5)

(defsc CountdownButton [this {:ui/keys [count]}]
  {:ident     [:ui/id :ui/id]
   :query     [:ui/id :ui/count]
   :pre-merge (fn [{:keys [current-normalized data-tree]}]
                (merge
                  {:ui/id    (random-uuid)
                   :ui/count default-count}
                  current-normalized
                  data-tree))}
  (let [done? (zero? count)]
    (dom/button {:disabled done?
                 :onClick  #(m/set-value! this :ui/count (dec count))}
      (if done? "Done!" (str count)))))

(def ui-countdown-button (comp/factory CountdownButton {:keyfn ::counter-id}))

(defsc Countdown [this {::keys   [counter-label counter-initial]
                        :ui/keys [counter]}]
  {:ident     [::counter-id ::counter-id]
   :query     [::counter-id ::counter-label ::counter-initial
               {:ui/counter (comp/get-query CountdownButton)}]
   :pre-merge (fn [{:keys [current-normalized data-tree]}]
                (let [initial (merge/nilify-not-found (::counter-initial data-tree))]
                  (log/spy :info (merge
                                   {:ui/counter (cond-> {} initial (assoc :ui/count initial))}
                                   current-normalized
                                   data-tree))))}
  (dom/div
    (dom/h4 (str counter-label " [" (or counter-initial default-count) "]"))
    (ui-countdown-button counter)))

(def ui-countdown (comp/factory Countdown {:keyfn ::counter-id}))

(defsc Root [this {::keys [all-counters]}]
  {:initial-state (fn [_] {::all-counters
                           [{::counter-id    (tempid/tempid)
                             ::counter-label "X"}
                            {::counter-id    (tempid/tempid)
                             ::counter-label "Y"}
                            {::counter-id      (tempid/tempid)
                             ::counter-label   "Z"
                             ::counter-initial 9}]})
   :query         [{::all-counters (comp/get-query Countdown)}]}
  (dom/div
    (dom/h3 "Counters")
    (when (seq all-counters)
      (dom/div {:style {:display "flex" :alignItems "center" :justifyContent "space-between"}}
        (mapv ui-countdown all-counters)))))
