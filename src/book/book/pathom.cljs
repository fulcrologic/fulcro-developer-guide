(ns book.pathom
  (:require
    [com.fulcrologic.fulcro.mutations :as m :refer [defmutation]]
    [com.fulcrologic.fulcro.networking.mock-server-remote :refer [mock-http-server]]
    [com.wsscode.pathom.core :as p]
    [com.wsscode.pathom.connect :as pc]))

(defonce latency (atom 100))

(defmutation set-server-latency [{:keys [delay]}]
  (action [{:keys [app state]}]
    (js/console.log "Latency set to" delay)
    (reset! latency delay)
    (swap! state assoc-in [:server-control/by-id :server :server-control/delay] delay)))

(defn new-parser [my-resolvers]
  (p/parallel-parser
    {::p/env     {::p/reader [p/map-reader
                              pc/parallel-reader
                              pc/open-ident-reader]}
     ::p/mutate  pc/mutate-async
     ::p/plugins [(pc/connect-plugin {::pc/register my-resolvers})
                  p/error-handler-plugin
                  p/request-cache-plugin
                  (p/post-process-parser-plugin p/elide-not-found)]}))

(defn mock-remote
  ([resolvers env]
   (let [parser    (new-parser resolvers)
         transmit! (:transmit! (mock-http-server {:parser (fn [req] (parser env req))}))]
     {:remote {:transmit! (fn [this send-node]
                            (js/setTimeout
                              #(transmit! this send-node)
                              @latency))}}))
  ([resolvers]
   (mock-remote resolvers {})))
