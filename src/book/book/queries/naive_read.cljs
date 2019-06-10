(ns book.queries.naive-read
  (:require [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
            [book.queries.parse-runner :refer [ParseRunner ui-parse-runner]]
            [com.fulcrologic.fulcro.dom :as dom]))

(defn read-42 [env key params] {:value 42})
(def parser-42 (comp/parser {:read read-42}))

(defsc Root [this {:keys [parse-runner]}]
  {:initial-state {:parse-runner {}}
   :query         [{:parse-runner (comp/get-query ParseRunner)}]}
  (dom/div
    (ui-parse-runner (comp/computed parse-runner {:parser parser-42 :database {}}))))
