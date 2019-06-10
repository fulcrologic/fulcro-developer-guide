(ns user
  (:require
    [clojure.pprint :refer [pprint]]
    [clojure.test :refer :all]
    [clojure.repl :refer [doc source]]
    [clojure.tools.namespace.repl :as tools-ns :refer [disable-reload! refresh clear set-refresh-dirs]]
    [expound.alpha :as expound]
    [clojure.spec.alpha :as s]))

(set-refresh-dirs "src/book" "src/dev")
(alter-var-root #'s/*explain-out* (constantly expound/printer))

