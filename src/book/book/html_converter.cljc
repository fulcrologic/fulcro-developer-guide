(ns book.html-converter
  (:require
    #?(:cljs [com.fulcrologic.fulcro.dom :as dom]
       :clj  [com.fulcrologic.fulcro.dom-server :as dom])
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    [com.fulcrologic.fulcro.mutations :as m :refer [defmutation]]
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

(defn element->call
  ([elem]
   (element->call "dom" elem))
  ([ns-alias elem]
   (cond
     (and (string? elem)
       (let [elem (str/trim elem)]
         (or
           (= "" elem)
           (and
             (str/starts-with? elem "<!--")
             (str/ends-with? elem "-->"))
           (re-matches #"^[ \n]*$" elem)))) nil
     (string? elem) (str/trim elem)
     (vector? elem) (let [tag       (name (first elem))
                          raw-props (second elem)
                          attrs     (cond-> (set/rename-keys raw-props attr-renames)
                                      (contains? raw-props :style) (update :style fix-style))
                          children  (keep (partial element->call ns-alias) (drop 2 elem))]
                      (concat (list (if ns-alias
                                      (symbol ns-alias tag)
                                      (symbol tag)) attrs) children))
     :otherwise "UNKNOWN")))

(defn html->clj-dom
  "Convert an HTML fragment (containing just one tag) into a corresponding Dom cljs"
  ([html-fragment {:keys [ns-alias] :as options}]
   (let [hiccup-list (map hc/as-hiccup (hc/parse-fragment html-fragment))]
     (let [result (keep (partial element->call ns-alias) hiccup-list)]
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
    (dom/pre {} (with-out-str (pprint (:code cljs))))
    (dom/button :.c-button {:onClick (fn [evt]
                                       (comp/transact! this `[(convert {})]))} "Convert")))

(def ui-html-convert (comp/factory HTMLConverter))

(defsc Root [this {:keys [converter]}]
  {:initial-state {:converter {}}
   :query         [{:converter (comp/get-query HTMLConverter)}]}
  (ui-html-convert converter))

