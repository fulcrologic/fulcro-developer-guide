(ns book.react-interop.stripe-example
  (:require
    ["react-stripe-elements" :as stripe :refer [StripeProvider Elements injectStripe
                                                CardNumberElement
                                                CardExpiryElement
                                                CardCvcElement]]
    [com.fulcrologic.fulcro.algorithms.react-interop :as interop]
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    [com.fulcrologic.fulcro.dom :as dom]
    [com.fulcrologic.fulcro.mutations :as m]))

(def test-key "pk_test_crQ5GM8nCR8tM06YZcgunnmZ00qjGWhSNV")

(def ui-stripe-provider (interop/react-factory StripeProvider))
(def ui-elements (interop/react-factory Elements))
(def ui-card-element (interop/react-factory CardNumberElement))
(def ui-exp-element (interop/react-factory CardExpiryElement))
(def ui-cvc-element (interop/react-factory CardCvcElement))

(defn handle-result [result]
  (js/console.log result)
  (let [{{:keys [message]} :error
         {:keys [id]}      :token} (js->clj result :keywordize-keys true)]
    (when message
      (js/alert (str "Error: " message)))
    (when id
      (js/alert (str "Created payment token " id)))))

(defsc CCForm [this {:customer/keys [name] :as props} {:keys [injected-props] :as computed}]
  {:query         [:customer/name]
   :initial-state {:customer/name ""}
   :ident         (fn [] [:component/id ::ccform])}
  ;; isoget is a util that gets props from js maps in cljs, or CLJ ones in clj
  (let [stripe (comp/isoget injected-props "stripe")]
    (dom/form :.ui.form
      {:onSubmit (fn [e] (.preventDefault e))}
      (dom/div :.field
        (dom/label "Name")
        (dom/input {:value    name
                    :onChange #(m/set-string! this :customer/name :event %)}))
      (dom/div :.field
        (dom/label "Card Number")
        (ui-card-element {}))
      (dom/div :.two.fields
        (dom/div :.field
          (dom/label "Expired")
          (ui-exp-element {}))
        (dom/div :.field
          (dom/label "CVC")
          (ui-cvc-element {})))
      (dom/button {:onClick (fn [e]
                              ;; createToken is a calls to the low-level JS API
                              (-> (.createToken stripe #js {:type "card" :name name})
                                (.then (fn [result] (handle-result result)))
                                (.catch (fn [result] (handle-result result)))))}
        "Pay"))))

(def ui-cc-form (interop/hoc-factory CCForm injectStripe))

(defsc Root [this {:keys [root/cc-form] :as props}]
  {:query         [{:root/cc-form (comp/get-query CCForm)}]
   :initial-state {:root/cc-form {}}}
  (dom/div
    (dom/h2 "Payment Form")
    (ui-stripe-provider #js {:apiKey test-key}
      (ui-elements #js {}
        ;; NOTE: Remember to pass parent's `this` to the child when using an HOC factory.
        (ui-cc-form this cc-form)))))

