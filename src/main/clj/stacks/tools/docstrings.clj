(ns stacks.tools.docstrings
  "Tools for extracting Clojure documentation from docstrings."
  {:authors ["Reid \"arrdem\" McKenzie <me@arrdem.com>"]
   :license "https://www.eclipse.org/legal/epl-v10.html"}
  (:require [stacks.tools.reader :refer [read-source]]))

(defn source-fn
  "Adapted from `#'clojure.repl/source-fn`. Returns a string of the
  source code for the given var, if it can find it. Returns nil if it
  can't find the source.

  Unlike `source-fn`, reads the source with `*ns*` bound to the
  namespace of the var whose source is to be read. This enables the
  reading of sources containing for instance `::foo` and `::foo/bar`
  style keywords whose reading depends on the state of `*ns*`.

  ```clj/session
  ---
  {:dependencies [[org.clojure/clojure \"1.9.0\"]]}
  ---
  stacks.tools.docstrings> (source-fn #'clojure.core/inc)
  \"(defn inc
    \\\"Returns a number one greater than num. Does not auto-promote
    longs, will throw on overflow. See also: inc'\\\"
    {:inline (fn [x] `(. clojure.lang.Numbers (~(if *unchecked-math* 'unchecked_inc 'inc) ~x)))
     :added \\\"1.2\\\"}
    [x] (. clojure.lang.Numbers (inc x)))\"
  stacks.tools.docstrings>
  "
  [v]
  {:pre [(var? v)]}
  (if-let [filepath (:file (meta v))]
    (if-let [strm (.getResourceAsStream (clojure.lang.RT/baseLoader) filepath)]
      (with-open [rdr (java.io.LineNumberReader. (java.io.InputStreamReader. strm))]
        (binding [*ns* (.ns v)]
          ;; Walk forwards through the reader to the desired line
          (dotimes [_ (dec (:line (meta v)))]
            (.readLine rdr))
          ;; Read the next form off the reader, returning its source
          (read-source rdr)))
      {:type     ::error
       :msg      "Unable to open `:resource` for reading via `clojure.lang.RT/baseLoader`."
       :var      v
       :resource filepath})
    {:type ::error
     :msg  "Var has no `:file` metadata."
     :var  v}))

(defn ->doc
  "Constructor.

  Builds & returns a documentation record."
  [thing meta]
  {:type ::docstring
   :obj  thing
   :text (:doc meta)
   :meta (dissoc meta :doc)})

(defprotocol
    ^{:doc "A protocol providing open dispatch for fetching documentation & metadata"}
    Documentable
  (doc* [this]))

(meta Documentable)

(extend-protocol Documentable
  nil
  (doc* [_] nil)

  clojure.lang.Namespace
  (doc* [ns]
    (->doc ns (meta ns)))

  clojure.lang.Var
  (doc* [var]
    (->doc var (meta var)))

  clojure.lang.Symbol
  (doc* [sym]
    (if (and (namespace sym)
             (name sym))
      (doc* (clojure.lang.Var/find sym))
      (doc* (clojure.lang.Namespace/find sym)))))

(defmacro doc
  "Same as `#'clojure.repl/doc`, just using my `doc*` abstraction."
  [sym]
  (doc* sym))
