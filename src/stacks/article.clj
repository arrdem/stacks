(ns stacks.article
  "A thin wrapper around markdown-clj which adds extensions for writing stacks articles."
  {:authors ["Reid McKenzie <me@arrdem.com>"]
   :license "https://www.eclipse.org/legal/epl-v10.html"}
  (:require [clojure.string :as string]
            [clojure.walk :as walk]
            [commonmark-hiccup.core :as mark]
            [clojure.java.io :as io])
  (:import [org.commonmark.node
            ,,FencedCodeBlock
            ,,Heading]))

(def kramdown-heading-pattern
  #"(?<heading>[^\{\}]*?)(\s*\{(?<attrs>[^\}]*)\})?$")

(defn parse-kramdown-attrs
  [text]
  (if-not text
    {}
    (->> (string/split text #"\s+")
         (map (fn [s]
                (cond (.startsWith s "#")
                      [:id (.replaceFirst s "#" "")]

                      (.startsWith s ".")
                      [:class (.replaceFirst s "." "")]

                      (.contains s "=")
                      (vec (string/split s #"=" 2)))))
         (reduce (fn [acc [k v]]
                   (case k
                     (:class) (update acc k (fnil conj []) v)
                     (assoc acc k v)))
                 {}))))

(defn parse-kramdown-suffix
  "https://kramdown.gettalong.org/syntax.html#specifying-a-header-id"
  [heading]
  (let [matcher (re-matcher kramdown-heading-pattern heading)]
    (if (.matches matcher)
      [(.group matcher "heading") (parse-kramdown-attrs (.group matcher "attrs"))]
      [heading {}])))

(defn parse-code-block
  ""
  [^FencedCodeBlock block]
  (let [[tag attrs] (parse-kramdown-suffix (.getInfo block))]
    {:type    ::code
     :content (.getLiteral block)
     :tag     tag
     :attrs   attrs}))

(defn munge-heading
  "Munge a heading to an ID"
  [^String heading]
  (-> heading
      (.toLowerCase)
      (.replaceAll "\\s" "-")))

(defn parse-heading
  ""
  [^Heading h]
  (let [[heading attrs] (parse-kramdown-suffix (mark/text-content h))]
    [(keyword (str "h" (.getLevel h)))
     (merge {:id (munge-heading heading)}
            attrs)
     heading]))

(defn deep-merge [v & vs]
  (letfn [(rec-merge [v1 v2]
            (if (and (map? v1) (map? v2))
              (merge-with deep-merge v1 v2)
              v2))]
    (when (some identity vs)
      (reduce #(rec-merge %1 %2) v vs))))

(def markdown-config
  ""
  (deep-merge mark/default-config
              {:renderer {:nodes {FencedCodeBlock parse-code-block
                                  Heading         parse-heading}}}))

(defn hiccup-tag?
  [o]
  (and (vector? o)
       (keyword? (first o))))

(defn prewalk-hiccup
  [f tree]
  (walk/prewalk #(if (hiccup-tag? %) (f %) %) tree))

(defn postwalk-hiccup
  [f tree]
  (walk/postwalk #(if (hiccup-tag? %) (f %) %) tree))

(defn collect-labels [tree]
  (let [acc (volatile! {})]
    (postwalk-hiccup
     (fn [[tag attrs rest :as %]]
       (when (#{:h1 :h2 :h3 :h4 :h5 :h6} tag)
         (vswap! acc assoc (:id attrs) rest))
       %)
     tree)
    @acc))

(defn collect-references [tree]
  (let [acc (volatile! {})]
    (postwalk-hiccup
     (fn [[tag attrs rest :as %]]
       (when (#{:a} tag)
         (vswap! acc assoc (:href attrs) rest))
       %)
     tree)
    @acc))

(defn- buffer->article
  ""
  [source-loc buffer]
  (let [content (mark/markdown->hiccup markdown-config buffer)]
    {:type       ::article
     :source     source-loc
     :labels     (collect-labels content)
     :references (collect-references content)
     :content    content}))

(defn markdown->article
  ""
  [resource-file-or-buffer]
  (if (instance? java.io.File resource-file-or-buffer)
    (reader->article (.toURL ^java.io.File resource-file-or-buffer) (slurp resource-file-or-buffer))
    (if-let [r (io/resource resource-file-or-buffer)]
      (reader->article (.toURL r) (slurp r))
      (if (string? resource-file-or-buffer)
        (reader->article (str "NO SOURCE AVAILABLE") (java.io.StringReader. resource-file-or-buffer))
        (throw
         (IllegalArgumentException.
          "Don't know what I got but couldn't convert it to a buffer for parsing!"))))))

