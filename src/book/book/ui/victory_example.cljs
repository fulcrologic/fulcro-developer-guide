(ns book.ui.victory-example
  (:require
    [cljs.pprint :refer [cl-format]]
    ;; REQUIRES shadow-cljs, with "victory" in package.json
    ["victory" :refer [VictoryChart VictoryAxis VictoryLine]]
    [com.fulcrologic.fulcro.dom :as dom]
    [com.fulcrologic.fulcro.algorithms.react-interop :as interop]
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    [taoensso.timbre :as log]))

(defn us-dollars [n]
  (str "$" (cl-format nil "~:d" n)))

(def vchart (interop/react-factory VictoryChart))
(def vaxis (interop/react-factory VictoryAxis))
(def vline (interop/react-factory VictoryLine))

;; " [ {:year 1991 :value 2345 } ...] "
(defsc YearlyValueChart [this {:keys [label plot-data x-step]}]
  (let [start-year (apply min (mapv :year plot-data))
        end-year   (apply max (mapv :year plot-data))
        years      (range start-year (inc end-year) x-step)
        dates      (mapv #(new js/Date % 1 2) years)
        {:keys [min-value
                max-value]} (reduce (fn [{:keys [min-value max-value] :as acc}
                                         {:keys [value] :as n}]
                                      (assoc acc
                                        :min-value (min min-value value)
                                        :max-value (max max-value value)))
                              {}
                              plot-data)
        min-value  (int (* 0.8 min-value))
        max-value  (int (* 1.2 max-value))
        points     (mapv (fn [{:keys [year value]}]
                           {:x (new js/Date year 1 2)
                            :y value})
                     plot-data)]
    (vchart nil
      (vaxis {:label      label
              :standalone false
              :scale      "time"
              :tickFormat (fn [d] (.getFullYear d))
              :tickValues dates})
      (vaxis {:dependentAxis true
              :standalone    false
              :tickFormat    (fn [y] (us-dollars y))
              :domain        #js [min-value max-value]})
      (vline {:data points}))))

(def yearly-value-chart (comp/factory YearlyValueChart))

(defsc Root [this props]
  {:initial-state {:label     "Yearly Value"
                   :x-step    2
                   :plot-data [{:year 1983 :value 20}
                               {:year 1984 :value 100}
                               {:year 1985 :value 90}
                               {:year 1986 :value 89}
                               {:year 1987 :value 88}
                               {:year 1988 :value 85}
                               {:year 1989 :value 83}
                               {:year 1990 :value 80}
                               {:year 1991 :value 70}
                               {:year 1992 :value 80}
                               {:year 1993 :value 90}
                               {:year 1994 :value 95}
                               {:year 1995 :value 110}
                               {:year 1996 :value 120}
                               {:year 1997 :value 160}
                               {:year 1998 :value 170}
                               {:year 1999 :value 180}
                               {:year 2000 :value 180}
                               {:year 2001 :value 200}]}}
  (dom/div
    (yearly-value-chart props)))

