(ns component1.core
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require [reagent.core :as reagent :refer [atom]]
            [clojure.string :as str]
            [cljs.core.async :as async :refer [<! chan put! timeout]]
            [cljs-http.client :as http]
            [cljs.pprint :as pp]
            [goog.events :as events]
            [goog.events.EventType :as EventType]
            [goog.dom :as dom]))

;;;(enable-console-print!)

(def cur-scroll-y (atom 0))
(def item-number (atom 50))
(def viewport-size (-> (dom/getViewportSize) (.-height)))
(defn debounce [in ms]
  (let [out (chan)]
    (go-loop [last-val nil]
      (let [val (if (nil? last-val) (<! in) last-val)
            timer (timeout ms)
            [new-val ch] (alts! [in timer])]
        (condp = ch
          timer (do (>! out val) (recur nil))
          in (if new-val (recur new-val)))))
    out))
(defn- get-scroll []
  (-> (dom/getDocumentScroll) (.-y)))
(defn- get-height []
  (-> (dom/getDocumentHeight)))
(defn- events->chan [el event-type c]
  (events/listen el event-type #(put! c %))
  (debounce c 10))
(def height (atom 0))

(defn scroll-chan-events []
  (events->chan js/window EventType/SCROLL (chan 1 (map get-scroll))))
(defn document-chan-events []
  (events->chan js/window EventType/SCROLL (chan 1 (map get-height))))
(defn listen! []
  (let [[chan chan2] [(scroll-chan-events) (document-chan-events)]]
    (go-loop []
      (let [[new-y new-height] [(<! chan) (<! chan2)]]
        (reset! cur-scroll-y new-y)
        (reset! height new-height)
        (recur)))))


(defn get-hackernews-url [value page]
  (let [url ["https://hn.algolia.com/api/v1/search?query=" value "&page=" page "&hitsPerPage=80"]]
    (str/join url)))
(def app-state (atom nil))

(defn update-results! [f & args]
  (apply swap! app-state update :results f args))

(defn add-result! [c]
  (update-results! concat c))

(def test-cond  (reagent.ratom/reaction (-  (+ @cur-scroll-y @height) viewport-size)))    ;; reaction wraps a computation, returns a signal
(def test-cond2  (reagent.ratom/reaction (if (>= @test-cond 0)
                                           (swap! item-number (partial + 5))
                                           @item-number)));; reaction wraps a computation, returns a signal




(defn search [value]
  (reset! app-state nil)
  (reset! item-number 50)
  (dotimes [n 100]
    (go
      (let [response (<! (http/get (get-hackernews-url value n) {:with-credentials? false :channel (chan 1)}))]
        ;;(pp/pprint (get-in  response [:body :hits]))
        (add-result! (get-in  response [:body :hits]))))))

(defn list-item [item]
  [:li.list-row
   [:a {:href (:url item)}  (:title item)]])

(defn r-list []
  [:ul.list
   (doall
    (let [testval (take @test-cond2 (:results  @app-state))]
      (for [i testval]
        ^{:key (:objectID i)}
        [list-item i])))])

(defn search-form  []
  (let [component-state (atom nil)]
    (fn []
      (let [val @component-state]
        [:div.interactions
         [:form {:type "submit"}
          [:input {:type "text" :value val  :on-change #(reset! component-state (-> % .-target .-value))}]
          [:button {:type "submit" :on-click
                    (fn [e]
                      (.preventDefault e)
                      (search val))} "Search"]]]))))

(defn app []
  (listen!)
  [:div.page
   [search-form]
   (if (:results @app-state) [r-list])])

;(reagent.ratom/run! (println  (- js/document.body.offsetHeight (+ js/window.innerHeight  @cur-scroll-y))))
(reagent/render-component [app]
                          (. js/document (getElementById "app")))

(defn on-js-reload [])
  ;; optionally touch your app-state to force rerendering depending on
  ;; your application
  ;; (swap! app-state update-in [:__figwheel_counter] inc)
