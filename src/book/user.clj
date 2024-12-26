(ns user
  (:require
    [clojure.spec.alpha :as s]
    [clojure.test :refer :all]
    [clojure.tools.namespace.repl :refer [set-refresh-dirs]]
    [expound.alpha :as expound]))

(set-refresh-dirs "src/book" "src/dev")
(alter-var-root #'s/*explain-out* (constantly expound/printer))

