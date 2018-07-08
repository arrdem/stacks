(ns stacks.tools.articles
  "A thin wrapper around common mark which adds extensions for writing stacks articles."
  {:authors ["Reid \"arrdem\" McKenzie <me@arrdem.com>"]
   :license "https://www.eclipse.org/legal/epl-v10.html"}
  (:require [clojure.string :as string]
            [clojure.walk :as walk]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [detritus.update :refer [deep-merge]]
            [commonmark-hiccup.core :as mark]
            [me.raynes.conch :refer [let-programs]])
  (:import [org.commonmark.node
            ,,FencedCodeBlock
            ,,Heading]))

(def kramdown-heading-pattern
  ""
  #"(?<heading>[^\{\}]*?)(\s*\{(?<attrs>.+)\})?\s*$")

(def kramdown-attr-pattern
  ""
  (re-pattern
   (string/join "|"
                [#"(?<id>#[\S&&[^\}]]+)"
                 #"(?<class>\.[\S&&[^\}]]+)"
                 #"(?<kv>(?<k>[\S&&[^\}=]]+)=(?<v>([\S&&[^\}\"]]+)|(\"([^\"]|\\\")*?\")))"])))

(defn parse-kramdown-attrs
  "Parses the contents of a Kramdown style {} trailing form on a heading or code block tag.

  `{}` forms are whitespace delimited lists of `#foo` forms which set
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

(defn handle-default [tag attrs raw]
  {:type ::code
   :tag tag
   :attrs attrs
   :raw raw})

(defn handle-pygmentize
  "Handle most code blocks by pygmentizing the content."
  [tag attrs raw]
  (if (Boolean/parseBoolean (get attrs :highlight "true"))
    (let-programs [pygmentize "pygmentize"]
      (pygmentize "-fhtml"
                  (str "-l" tag)
                  "-Ostripnl=False,encoding=utf-8"
                  {:in raw}))
    (handle-default tag attrs raw)))

(defn pygments-lexers []
  (let-programs [pygmentize "pygmentize"]
    (->> (pygmentize "-L" "lexers")
         (re-seq #"(?sm)^\* (((, )?[^:,\s]+)+):")
         (mapcat (fn [[_text names]]
                   (str/split names #", "))))))

(defn handle-graphviz
  "Handle graphviz by compiling it ... to inline image data I guess?

  Unless render=false."
  [tag attrs raw]
  (if (Boolean/parseBoolean (get attrs :render "false"))
    (let-programs [graphviz "dot"]
      [:div {:class #{"highlight"}}
       (graphviz "-Tsvg"
                 {:in raw})])
    (handle-default tag attrs raw)))

(defn handle-session [tag attrs raw]
  (if (Boolean/parseBoolean (get attrs :render "false"))
    (do (require 'stacks.tools.sessions)
        (as-> raw %
          ((resolve 'stacks.tools.sessions/parse-session) %)
          ((resolve 'stacks.tools.sessions/render-session) %)))
    (handle-default tag attrs raw)))

(defn handle-doctest [tag attrs raw]
  (if (Boolean/parseBoolean (get attrs :render "false"))
    (do (require 'stacks.tools.doctests)
        ((resolve 'stacks.tools.doctests/parse-doctests) raw))
    (handle-default tag attrs raw)))

(defn parse-code-block
  "Takes a FencedCodeBlock instance, and returns a `::code` tagged structure with the contents.

  Interprets the first line of the block as \"info\", consisting of
  the name of the language (or formatter) to use on the block followed
  by optional Kramdown [1] attributes.

  Attempts to parse a couple known document formats to data
  structures, but by default returns a structure of the form

  ```
  {:type  ::code
   :raw   String ; raw content
   :tag   String ; tag less any Kramdown attributes.
   :attrs Map    ; parsed Kramdown attributes.
  }
  ```

  Handlers support the attributes \"render\" and
  \"highlight\". \"highlight\" is true by default, and directs a
  handler to produce a \"pretty\" form of the given text. \"render\"
  is false by default, and directs the handler to produce an ARBITRARY
  product from the text - for instance an SVG image.

  [1] https://kramdown.gettalong.org/syntax.html#specifying-a-header-id"
  [handlers ^FencedCodeBlock block]
  (let [[tag attrs] (parse-kramdown-suffix (.getInfo block))
        raw (.getLiteral block)
        handler (get handlers tag (get handlers ::default-handler))]

    (handler tag attrs raw)))

(defn munge-heading
  "Munge a heading (h1 h2 etc.) to a string that could be used as an ID."
  [^String heading]
  (-> heading
      (.toLowerCase)
      (.replaceAll "[\\s\\p{Punct}]+" "-")))

(defn parse-heading
  "Takes a Heading instance, and returns an appropriate Hiccup `:h`
  structure, adding a computed `:id` based on the heading's name."
  [^Heading h]
  (let [[heading attrs] (parse-kramdown-suffix (mark/text-content h))]
    [(keyword (str "h" (.getLevel h)))
     (merge {:id (munge-heading heading)}
            attrs)
     heading]))

(defn markdown-config
  "An extended Markdown processor.

  Adds support for my fenced code block notation and my labeled headings."
  [handlers]
  (deep-merge mark/default-config
              {:renderer
               {:nodes
                {FencedCodeBlock (partial parse-code-block handlers)
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

(defn parse-article
  "Takes a location and a buffer read from that location. Produces an `::article`.

  Articles are tagged unions, having `:source` being where the source
  used to compute the structure came from, `:labels` being the set of
  labels created in this article, `:references` being the set of
  labels / link targets used in this article and `:content` being a
  tree of Hiccup vectors and tagged unions representing the article."
  [handlers source-loc buffer]
  (let [content (mark/markdown->hiccup (markdown-config handlers) buffer)]
    {:type       ::article
     :source     source-loc
     :labels     (collect-labels content)
     :references (collect-references content)
     :content    content}))

(def +default-handlers+
  (-> {"clj+session" handle-session
       "clj+doctest" handle-doctest
       "dot" handle-graphviz
       ::default-handler handle-default}
      (as-> % 
        (reduce #(assoc %1 %2 handle-pygmentize)
                % (pygments-lexers))
        ;; FIXME: Pygments doesn't support cljc
        (if-not (get % "cljc")
          (assoc % "cljc" (get % "clj"))
          %))))

(defn markdown->article
  "Takes a file path, resource path, File instance or raw buffer and parses it to an `::article`."
  ([resource-file-or-buffer]
   (markdown->article +default-handlers+
                      resource-file-or-buffer))
  ([handlers resource-file-or-buffer]
   (or (when (instance? java.io.File resource-file-or-buffer)
         (parse-article handlers
                        (io/as-url resource-file-or-buffer)
                        (slurp resource-file-or-buffer)))

       (let [f (io/file resource-file-or-buffer)]
         (when (.exists f)
           ;; Can't recur from here >.>
           (markdown->article handlers f)))

       (if-let [r (io/resource resource-file-or-buffer)]
         (parse-article handlers
                        (io/as-url r)
                        (slurp r)))

       (when (string? resource-file-or-buffer)
         (parse-article handlers
                        (str "NO SOURCE AVAILABLE")
                        resource-file-or-buffer))

       (throw
        (IllegalArgumentException.
         "Don't know what I got but couldn't convert it to a buffer for parsing!")))))
