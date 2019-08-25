(ns book.react-interop.google-maps-example
  (:require
    ["google-maps-react" :as maps :refer [GoogleApiWrapper Map Marker]]
    [com.fulcrologic.fulcro.algorithms.react-interop :as interop :refer [react-factory]]
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    [com.fulcrologic.fulcro.dom :as dom]))

;; Fulcro wrapper factory for google-maps-react's Map component
(def ui-google-map (react-factory Map))

;; Another wrapper factory for Marker component
(def ui-map-marker (react-factory Marker))

(defsc LocationView [this {:map/keys [latitude longitude] :as props} {:keys [injected-props] :as computed}]
  {:query         [:map/latitude :map/longitude]
   :ident         (fn [] [:component/id ::location-view])
   :initial-state {:map/latitude 45.5051 :map/longitude -122.6750}}
  (dom/div
    (dom/div {:style {:width "520px" :height "520px"}}
      ;; The API docs say the the HOC adds a "google" prop that should be passed to Map
      (ui-google-map {:google        (.-google injected-props)
                      :zoom          5
                      :initialCenter {:lat latitude :lng longitude}
                      :style         {:width "90%" :height "90%"}}
        (ui-map-marker {:position {:lat latitude :lng longitude}})))))

(def injectGoogle (GoogleApiWrapper #js {:apiKey "AIzaSyAM9PWfj-rJDnmyJvh8QNkO4pgGzy5s_Yg"}))
(def ui-location-view (interop/hoc-factory LocationView injectGoogle))

(defsc Root [this {:root/keys [location-view] :as props}]
  {:query         [{:root/location-view (comp/get-query LocationView)}]
   :initial-state {:root/location-view {}}}
  (dom/div
    (dom/h3 "A Map View")
    (ui-location-view this location-view)))
