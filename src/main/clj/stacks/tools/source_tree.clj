(ns stacks.tools.source-tree
  "Render project sources to a nice sidebar tree."
  (:use [hiccup core page element])
  (:import [java.net URLEncoder]
           [java.io File])
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.pprint :as pp]
            [clojure.string :as str]
            [clojure.walk :as walk]))

(defn split-ns [namespace]
  (str/split (str namespace) #"\."))

(defn namespace-parts [namespace]
  (->> (split-ns namespace)
       (reductions #(str %1 "." %2))
       (map symbol)))

(defn add-depths [namespaces]
  (->> namespaces
       (map (juxt identity (comp count split-ns)))
       (reductions (fn [[_ ds] [ns d]] [ns (cons d ds)]) [nil nil])
       (rest)))

(defn add-heights [namespaces]
  (for [[ns ds] namespaces]
    (let [d (first ds)
          h (count (take-while #(not (or (= d %) (= (dec d) %))) (rest ds)))]
      [ns d h])))

(defn add-branches [namespaces]
  (->> (partition-all 2 1 namespaces)
       (map (fn [[[ns d0 h] [_ d1 _]]] [ns d0 h (= d0 d1)]))))

(defn namespace-hierarchy [namespaces]
  (->> (map :name namespaces)
       (sort)
       (mapcat namespace-parts)
       (distinct)
       (add-depths)
       (add-heights)
       (add-branches)))

(defn index-by [f m]
  (into {} (map (juxt f identity) m)))

;; The values in ns-tree-part are chosen for aesthetic reasons, based
;; on a text size of 15px and a line height of 31px.

(defn ns-tree-part [height]
  (if (zero? height)
    [:span.tree [:span.top] [:span.bottom]]
    (let [row-height 31
          top        (- 0 21 (* height row-height))
          height     (+ 0 30 (* height row-height))]
      [:span.tree {:style (str "top: " top "px;")}
       [:span.top {:style (str "height: " height "px;")}]
       [:span.bottom]])))

(defn index-link [project on-index?]
  (list
   [:h3.no-link [:span.inner "Project"]]
   [:ul.index-link
    [:li.depth-1 {:class (if on-index? "current")}
     (link-to "index.html" [:div.inner "Index"])]]))

(defn nested-namespaces
  [link-to-source sources current-source?]
  (let [ns-map (index-by :name sources)]
    [:ul
     (for [[name depth height branch?] (namespace-hierarchy sources)]
       (let [class  (str "depth-" depth (if branch? " branch"))
             short  (last (split-ns name))
             inner  [:div.inner (ns-tree-part height) [:span (h short)]]]
         (if-let [ns (ns-map name)]
           (let [class (str class (if (= ns current-source?) " current"))]
             [:li {:class class} (link-to (link-to-source ns) inner)])
           [:li {:class class} [:div.no-link inner]])))]))

(defn doctree->source-tree
  "Given a function of a source to a URL path and a seq of sources
  compile them into a link tree like the one codox generates.

  Optionally accepts the \"current\" source's name."
  [link-to-source sources & [current-source?]]
  (nested-namespaces link-to-source sources current-source?))
