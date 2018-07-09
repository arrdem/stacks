(ns stacks.tools.doctest-runner
  "Compiling and registering doctests as a side-effect."
  (:refer-clojure :exclude [namespace name])
  (:require [stacks.tools.docstrings
             :refer [doc]]
            [stacks.tools.articles
             :as a
             :refer [handle-parse-block parse-article* tagged-union?]]
            [stacks.tools.doctests
             :refer [parse-doctests compile-doctests default-profile]]
            [detritus :refer [name namespace]]
            [clojure.string :as str])
  (:import [java.net URI]
           [clojure.lang Var]))

(defn- -parse-doctest [^clojure.lang.Namespace ns text]
  (let [profile (assoc default-profile :namespace (ns-name ns))]
    (parse-doctests profile text)))

(defn chain
  "Compose for 0-arity test functions.

  ```clj+doctest
  >> (chain #(println \"foo\"))
  => (fn? %)
  => (nil? (%))
  ```
  "
  [& fs]
  (when-let [fs (remove (complement fn?) fs)]
    (fn []
      (doseq [f fs]
        (f)))))

(defn var-uri
  "Returns a somewhat normalized URI, representing the var docstring source of an article."
  [var]
  (URI. "clj+var" (str (name (namespace var)) \/ (name var)) "#docstring"))

(defn max-by [f coll]
  (->> (map (juxt f identity) coll)
       (sort-by first)
       last
       second))

(defn normalize-lines
  "Normalize a docstring, stripping the most common prefix of whitespace from all line."
  ;; FIXME (arrdem 2018-01-07):
  ;;   Shit implementation.
  [text]
  (let [most-frequent-prefix (->> text
                                  (re-seq #"(?:\n|^)([\t ]+)")
                                  (map second)
                                  frequencies
                                  (max-by second)
                                  first)]
    (str/replace text (re-pattern (str "\n" most-frequent-prefix)) "\n")))

(defn install-doctests!
  "Attempt to parse all docstrings of all vars functions and in all loaded for doctests.

  When doctests are found, compile them in the namespace where they
  occur unless they specify another namespace. Install compiled test
  functions as `^:test` metadata on the var from whose docstring they
  were compiled.

  Accepts an optional sequence of patterns for restricting what
  namespaces are analyzed for doctests."
  ([]
   (install-doctests! [#".*"]))
  ([patterns]
   (doseq [a-ns        (all-ns)
           :when       (some #(re-find % (name a-ns)) patterns)
           :let        [m    (ns-map a-ns)
                        vars (filter (fn [[_ o]]
                                       (and (instance? Var o)
                                            (= a-ns (.ns ^Var o))))
                                     m)]
           [_name var] vars
           :let        [doc? (some-> var doc :text normalize-lines)]
           :when       doc?]
     (let [doctests (volatile! [])
           _ (parse-article* (fn [tag attrs raw]
                               (when (= tag "clj+doctest")
                                 (vswap! doctests conj (-parse-doctest a-ns raw)))
                               (handle-parse-block tag attrs raw))
                             (var-uri var)
                             doc?)]
       (try
         (let [doctest-fns (keep compile-doctests @doctests)]
           (when (not-empty doctest-fns)
             (alter-meta! var update :test (partial apply chain) doctest-fns)
             (binding [*out* *err*]
               (printf "Installed doctests on %s\n" var))))
         (catch Exception e
           (ex-info (format "Failed to install doctests on var %s" var)
                    {:content @doctests
                     :var     var
                     :ns      a-ns})))))))
