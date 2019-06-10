(ns book.queries.simple-property-read
  (:require [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
            [book.queries.parse-runner :refer [ParseRunner ui-parse-runner]]
            [com.fulcrologic.fulcro.dom :as dom]))

(defn property-read [{:keys [state]} key params] {:value (get @state key :not-found)})
(def property-parser (comp/parser {:read property-read}))

(defsc Root [this {:keys [parse-runner]}]
  {:initial-state {:parse-runner {}}
   :query         [{:parse-runner (comp/get-query ParseRunner)}]}
  (dom/div
    (ui-parse-runner (comp/computed parse-runner {:parser property-parser :database {:a 1 :b 2 :c 99}}))))

