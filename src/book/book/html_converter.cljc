(ns book.html-converter
  (:require
    #?(:cljs [com.fulcrologic.fulcro.dom :as dom]
       :clj  [com.fulcrologic.fulcro.dom-server :as dom])
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    [com.fulcrologic.fulcro.mutations :as m :refer [defmutation]]
    [com.fulcrologic.fulcro.dom.html-entities :as ent]
    [taoensso.timbre :as log]
    [camel-snake-kebab.core :as csk]
    [hickory.core :as hc]
    [clojure.set :as set]
    [clojure.pprint :refer [pprint]]
    [clojure.string :as str]))

(def attr-renames {:class        :className
                   :for          :htmlFor
                   :tabindex     :tabIndex
                   :viewbox      :viewBox
                   :spellcheck   :spellcheck
                   :autocorrect  :autoCorrect
                   :autocomplete :autoComplete})

(defn fix-style [style]
  (try
    (let [lines     (str/split style #";")
          style-map (into {} (map (fn [line]
                                    (let [[k v] (str/split line #":")]
                                      [(csk/->camelCase (keyword k)) (str/trim v)])) lines))]

      style-map)
    (catch #?(:cljs :default :clj Exception) e
      style)))

(defn classes->keyword [className]
  (when (seq (str/trim className))
    (let [classes (keep (fn [e] (when (seq e) e)) (str/split className #"  *"))
          kw      (keyword (str "." (str/join "." classes)))]
      kw)))

(defn- chars->entity [ns chars]
  (if (= \# (first chars))
    (apply str "&" (conj chars ";"))                        ; skip it. needs (parse int, convert base, format to 4-digit code)
    (symbol ns (apply str chars))))

(defn- parse-entity [stream result {:keys [entity-ns] :as options}]
  (let [ens (or entity-ns "ent")]
    (loop [s stream chars []]
      (let [c (first s)]
        (case c
          (\; nil) [(rest s) (if (seq chars)
                               (conj result (chars->entity ens chars))
                               result)]
          (recur (rest s) (conj chars c)))))))

(defn- html-string->react-string [stream options]
  (loop [s stream result []]
    (let [c (first s)
          [new-stream new-result] (case c
                                    nil [nil result]
                                    \& (parse-entity (rest s) result options)
                                    [(rest s) (conj result c)])]
      (if new-stream
        (recur new-stream new-result)
        (let [segments (partition-by char? new-result)
              result   (mapv (fn [s]
                               (if (char? (first s))
                                 (apply str s)
                                 (first s))) segments)]
          result)))))

(defn element->call
  ([elem]
   (element->call elem {}))
  ([elem {:keys [ns-alias keep-empty-attrs?] :as options}]
   (cond
     (and (string? elem)
       (let [elem (str/trim elem)]
         (or
           (= "" elem)
           (and
             (str/starts-with? elem "<!--")
             (str/ends-with? elem "-->"))
           (re-matches #"^[ \n]*$" elem)))) nil
     (string? elem) (html-string->react-string (str/trim elem) options)
     (vector? elem) (let [tag               (name (first elem))
                          raw-props         (second elem)
                          classkey          (when (contains? raw-props :class)
                                              (classes->keyword (:class raw-props)))
                          attrs             (cond-> (set/rename-keys raw-props attr-renames)
                                              (contains? raw-props :class) (dissoc :className)
                                              (contains? raw-props :style) (update :style fix-style))
                          children          (keep (fn [c] (element->call c options)) (drop 2 elem))
                          expanded-children (reduce
                                              (fn [acc c]
                                                (if (vector? c)
                                                  (concat acc c)
                                                  (conj acc c)))
                                              []
                                              children)]
                      (concat (list) (keep identity
                                       [(if ns-alias
                                          (symbol ns-alias tag)
                                          (symbol tag))
                                        (when classkey classkey)
                                        (if keep-empty-attrs?
                                          attrs
                                          (when (seq attrs) attrs))]) expanded-children))
     :otherwise "")))

(defn html->clj-dom
  "Convert an HTML fragment (containing just one tag) into a corresponding Dom cljs"
  ([html-fragment {:keys [ns-alias] :as options}]
   (let [hiccup-list (map hc/as-hiccup (hc/parse-fragment html-fragment))]
     (let [result (keep (fn [e] (element->call e options)) hiccup-list)]
       (if (< 1 (count result))
         (vec result)
         (first result)))))
  ([html-fragment]
   (html->clj-dom html-fragment {:ns-alias "dom"})))

(defmutation convert [p]
  (action [{:keys [state]}]
    (let [html (get-in @state [:top :conv :html])
          cljs (html->clj-dom html)]
      (swap! state assoc-in [:top :conv :cljs] {:code cljs}))))

(defsc HTMLConverter [this {:keys [html cljs]}]
  {:initial-state (fn [params] {:html "<div id=\"3\" class=\"b\"><p>Paragraph</p></div>" :cljs {:code (list)}})
   :query         [:cljs :html]
   :ident         (fn [] [:top :conv])}
  (dom/div {:className ""}
    (dom/textarea {:cols     80 :rows 10
                   :onChange (fn [evt] (m/set-string! this :html :event evt))
                   :value    html})
    (dom/button :.c-button {:onClick (fn [evt]
                                       (comp/transact! this `[(convert {})]))} "Convert")
    (dom/pre {} (with-out-str (pprint (:code cljs))))))

(def ui-html-convert (comp/factory HTMLConverter))

(defsc Root [this {:keys [converter]}]
  {:initial-state {:converter {}}
   :query         [{:converter (comp/get-query HTMLConverter)}]}
  (ui-html-convert converter))

