(ns component1.core
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [reagent.core :as reagent :refer [atom]]
            [clojure.string :as str]
            [cljs.core.async :as async :refer [<! chan]]
            [cljs-http.client :as http]
            [cljs.pprint :as pp]))

(enable-console-print!)

(println "!!!This text is printed from src/component1/core.cljs. Go ahead and edit it and see reloading in action.")

;; define your app data so that it doesn't get over-written on reload
(defn get-hackernews-url [value page]
  (let [url ["https://hn.algolia.com/api/v1/search?query=" value "&page=" page "&hitsPerPage=2"]]
    (str/join url)))




(defonce app-state (atom nil))

(defn search [value]
  (dotimes [n 10]
    (go (let [response (<! (http/get (get-hackernews-url value n ){:with-credentials? false :channel (chan 1)}))]
          (swap! app-state assoc :results conj (response [:body :hits]))))))



(defn r-list []
  [:div.list
    (doall
      (for [i (get @app-state :results)]
        [:div.list-row
          [:a {:href (:url i)}  (:title i)]]))])





(defn search-form  []
  (let [component-state (atom nil)]
    (fn []
      (let [val @component-state]
        [:div.interactions
          [:form {:type "submit" :on-submit (fn [e] (.-preventDefault e) (search val))}
           [:input {:type "text" :value val  :on-change #(reset! component-state (-> % .-target .-value))}]
           [:input {:type "submit" :value "Search"}]]]))))

(defn app []
    [:div.page
     [search-form]
     [r-list]])


(reagent/render-component [app]
                          (. js/document (getElementById "app")))

(defn on-js-reload [])
  ;; optionally touch your app-state to force rerendering depending on
  ;; your application
  ;; (swap! app-state update-in [:__figwheel_counter] inc)
