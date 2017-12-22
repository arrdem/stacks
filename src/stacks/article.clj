(ns stacks.article
  "A thin wrapper around common mark which adds extensions for writing stacks articles."
  {:authors ["Reid McKenzie <me@arrdem.com>"]
   :license "https://www.eclipse.org/legal/epl-v10.html"}
  (:require [clojure.string :as string]
            [clojure.walk :as walk]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [stacks.sessions :as sessions]
            [commonmark-hiccup.core :as mark])
  (:import [org.commonmark.node
            ,,FencedCodeBlock
            ,,Heading]))

(def kramdown-heading-pattern
  ""
  #"(?<heading>[^\{\}]*?)(\s*\{(?<attrs>.+)\})?\s*$")

(def kramdown-attr-pattern
  (re-pattern
   (string/join "|"
                [#"(?<id>#[\S&&[^\}]]+)"
                 #"(?<class>\.[\S&&[^\}]]+)"
                 #"(?<kv>(?<k>[\S&&[^\}=]]+)=(?<v>([\S&&[^\}\"]]+)|(\"([^\"]|\\\")*?\")))"])))

(defn parse-kramdown-attrs
  ""
  [text]
  (if-not text
    {}
    (->> text
         (re-seq kramdown-attr-pattern)
         (map (fn [[match id class kv k v unquoted-v quoted-v]]
                (let [;; FIXME (arrdem 2017-12-22):
                      ;;   Really I just need a thing to process escaped strings, but EDN works
                      v (or unquoted-v (edn/read-string quoted-v))]
                  (cond id    [:id (.replaceFirst id "#" "")]
                        class [:class (.replaceFirst class "." "")]
                        kv    [(keyword k) v]))))
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

(defn maybe-parse-session
  [{:keys [type tag] :as e}]
  (if-not (and (= type ::code)
               (= tag "clj/session"))
    e
    (update e :content sessions/parse-session)))

(defn parse-code-block
  ""
  [^FencedCodeBlock block]
  (let [[tag attrs] (parse-kramdown-suffix (.getInfo block))]
    (-> {:type    ::code
         :content (.getLiteral block)
         :tag     tag
         :attrs   attrs}
        maybe-parse-session)))

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

(defn deep-merge
  ""
  [v & vs]
  (letfn [(rec-merge [v1 v2]
            (if (and (map? v1) (map? v2))
              (merge-with deep-merge v1 v2)
              v2))]
    (when (some identity vs)
      (reduce #(rec-merge %1 %2) v vs))))

(def markdown-config
  "An extended Markdown processor.

  Adds support for my fenced code block notation and my labeled headings."
  (deep-merge mark/default-config
              {:renderer {:nodes {FencedCodeBlock parse-code-block
                                  Heading         parse-heading}}}))

(defn hiccup-tag?
  ""
  [o]
  (and (vector? o)
       (keyword? (first o))))

(defn tagged-union?
  ""
  [o]
  (and (map? o)
       (:type o)))

(defn postwalk-hiccup
  ""
  [f tree]
  (walk/postwalk #(if (hiccup-tag? %) (f %) %) tree))

(defn postwalk-tagged
  ""
  [f tree]
  (walk/postwalk #(if (tagged-union? %) (f %) %) tree))

(defn collect-labels
  ""
  [tree]
  (let [acc (volatile! #{})]
    (postwalk-hiccup
     (fn [[tag attrs :as %]]
       (when (:id attrs)
         (vswap! acc conj (:id attrs)))
       %)
     tree)
    @acc))

(defn collect-references
  ""
  [tree]
  (let [acc (volatile! #{})]
    (postwalk-hiccup
     (fn [[tag attrs :as %]]
       (when (#{:a} tag)
         (vswap! acc conj (:href attrs)))
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
    (buffer->article (.toURL ^java.io.File resource-file-or-buffer)
                     (slurp resource-file-or-buffer))

    (if-let [r (io/resource resource-file-or-buffer)]
      (buffer->article (.toURL r)
                       (slurp r))
      
      (if (string? resource-file-or-buffer)
        (buffer->article (str "NO SOURCE AVAILABLE")
                         resource-file-or-buffer)
        
        (throw
         (IllegalArgumentException.
          "Don't know what I got but couldn't convert it to a buffer for parsing!"))))))
