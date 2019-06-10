(ns book.queries.parsing-simple-join
  (:require [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
            [book.queries.parse-runner :refer [ParseRunner ui-parse-runner]]
            [com.fulcrologic.fulcro.dom :as dom]))

(def flat-app-state {:a 1 :user/name "Sam" :c 99})

(defn flat-state-read [{:keys [state parser query] :as env} key params]
  (if (= :user key)
    {:value (parser env query)}                             ; recursive call. query is now [:user/name]
    {:value (get @state key)}))                             ; gets called for :user/name :a and :c

(def my-parser (comp/parser {:read flat-state-read}))

(defsc Root [this {:keys [parse-runner]}]
  {:initial-state (fn [params] {:parse-runner (comp/get-initial-state ParseRunner {:query "[:a {:user [:user/name]} :c]"})})
   :query         [{:parse-runner (comp/get-query ParseRunner)}]}
  (dom/div
    (ui-parse-runner (comp/computed parse-runner {:parser my-parser :database flat-app-state}))))
