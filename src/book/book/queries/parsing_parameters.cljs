(ns book.queries.parsing-parameters
  (:require [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
            [book.queries.parse-runner :refer [ParseRunner ui-parse-runner]]
            [com.fulcrologic.fulcro.dom :as dom]))

(def database {
               :load/start-time 40000.0                     ;stored in ms
               })

(defn convert [ms units]
  (case units
    :minutes (/ ms 60000.0)
    :seconds (/ ms 1000.0)
    :ms ms))

(defn read [{:keys [state]} key params]
  (case key
    :load/start-time {:value (convert (get @state key) (or (:units params) :ms))}
    nil))

(def parser (comp/parser {:read read}))


(defsc Root [this {:keys [parse-runner]}]
  {:initial-state (fn [params] {:parse-runner (comp/get-initial-state ParseRunner {:query "[(:load/start-time {:units :seconds})]"})})
   :query         [{:parse-runner (comp/get-query ParseRunner)}]}
  (dom/div
    (ui-parse-runner (comp/computed parse-runner {:parser parser :database database}))))
