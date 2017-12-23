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
  "Parses the contents of a Kramdown style {} trailing form on a heading or code block tag.

  {} forms are whitespace delimited lists of `#foo` forms which set
  the ID of the decorated heading, `.foo` forms which add a class to
  the decorated heading, and `key=val` pairs where `val` may be a
  double quoted string containing whitespace."
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
  "Tries to parse a Kramdown [1] style heading suffix.

  Returns a pair `[heading, attrs]` where `heading` is a string of the
  un-parsed text, and `attrs` is a possibly empty map of keys and
  values specified in the suffix if any.

  [1] https://kramdown.gettalong.org/syntax.html#specifying-a-header-id"
  [heading]
  (let [matcher (re-matcher kramdown-heading-pattern heading)]
    (if (.matches matcher)
      [(.group matcher "heading") (parse-kramdown-attrs (.group matcher "attrs"))]
      [heading {}])))

(defn maybe-parse-session
  "`::code` structure transformer.

  If the `::code` structure is tagged `clj/session`, uses the
  `sessions` library to parse the session into a datastructure,
  returning an updated `::code` record containing the parsed session
  as its `:content`.

  If the tags didn't match, returns the argument `::code` record."
  [{:keys [type tag] :as e}]
  (if-not (and (= type ::code)
               (= tag "clj/session"))
    e
    (update e :content sessions/parse-session)))

(defn parse-code-block
  "Takes a FencedCodeBlock instance, and returns a `::code` tagged structure with the contents.

  Interprets the first line of the block as \"info\", consisting of
  the name of the language (or formatter) to use on the block followed
  by optional Kramdown [1] attributes.

  Attempts to parse a couple known document formats to data
  structures, but by default returns a structure of the form

  ```
  {:type     ::code
   :contennt String ; raw content
   :tag      String ; tag less any Kramdown attributes.
   :attrs    Map    ; parsed Kramdown attributes.
  }
  ```

  [1] https://kramdown.gettalong.org/syntax.html#specifying-a-header-id"
  [^FencedCodeBlock block]
  (let [[tag attrs] (parse-kramdown-suffix (.getInfo block))]
    (-> {:type    ::code
         :content (.getLiteral block)
         :tag     tag
         :attrs   attrs}
        maybe-parse-session)))

(defn munge-heading
  "Munge a heading (h1 h2 etc.) to a string that could be used as an ID."
  [^String heading]
  (-> heading
      (.toLowerCase)
      (.replaceAll "\\s" "-")))

(defn parse-heading
  "Takes a Heading instance, and returns an appropriate Hiccup `:h`
  structure, adding a computed `:id` based on the heading's name."
  [^Heading h]
  (let [[heading attrs] (parse-kramdown-suffix (mark/text-content h))]
    [(keyword (str "h" (.getLevel h)))
     (merge {:id (munge-heading heading)}
            attrs)
     heading]))

(defn- deep-merge
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
  "Predicate.

  Matches Hiccup style vectors of keywords and bodies."
  [o]
  (and (vector? o)
       (keyword? (first o))))

(defn tagged-union?
  "Predicate.

  Matches maps with the `:type` keyword in the tagged union style."
  [o]
  (and (map? o)
       (:type o)))

(defn postwalk-hiccup
  "Transformer.

  Walks a tree of Hiccup and other structures in deepest-first order,
  applying the given transformer to any `hiccup?` substructures.

  Returns the transformed tree."
  [f tree]
  (walk/postwalk #(if (hiccup-tag? %) (f %) %) tree))

(defn postwalk-tagged
  "Transformer.

  Walks a tree of Hiccup and other structures in deepest-first order,
  applying the given transformer to any `tagged-union?` substructures.

  Returns the transformed tree."
  [f tree]
  (walk/postwalk #(if (tagged-union? %) (f %) %) tree))

(defn collect-labels
  "Collects the set of all `:id`s specified in a Hiccup tree."
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
  "Collects the set of all `:href` targets specified in a Hiccup tree."
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
  "Takes a location and a buffer read from that location. Produces an `::article`.

  Articles are tagged unions, having `:source` being where the source
  used to compute the structure came from, `:labels` being the set of
  labels created in this article, `:references` being the set of
  labels / link targets used in this article and `:content` being a
  tree of Hiccup vectors and tagged unions representing the article."
  [source-loc buffer]
  (let [content (mark/markdown->hiccup markdown-config buffer)]
    {:type       ::article
     :source     source-loc
     :labels     (collect-labels content)
     :references (collect-references content)
     :content    content}))

(defn markdown->article
  "Takes a file path, resource path, File instance or raw buffer and parses it to an `::article`."
  [resource-file-or-buffer]
  (or (when (instance? java.io.File resource-file-or-buffer)
        (buffer->article (.toURL ^java.io.File resource-file-or-buffer)
                         (slurp resource-file-or-buffer)))

      (let [f (io/file resource-file-or-buffer)]
        (when (.exists f)
          ;; Can't recur from here >.>
          (markdown->article f)))

      (if-let [r (io/resource resource-file-or-buffer)]
        (buffer->article (.toURL r)
                         (slurp r)))
      
      (when (string? resource-file-or-buffer)
        (buffer->article (str "NO SOURCE AVAILABLE")
                         resource-file-or-buffer))
      
      (throw
       (IllegalArgumentException.
        "Don't know what I got but couldn't convert it to a buffer for parsing!"))))
