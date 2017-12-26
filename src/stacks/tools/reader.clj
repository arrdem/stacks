(ns stacks.tools.reader
  "Helpers for working with readers and reading files."
  (:require [clojure.java.io :as io]))

(defn read-clj
  "Private implementation detail copied from tools.namespace.

  Calls clojure.core/read. If reader conditionals are
  supported (Clojure 1.7) then adds options {:read-cond :allow}."
  [rdr]
  (if (resolve 'clojure.core/reader-conditional?)
    (read {:read-cond :allow} rdr)
    (read rdr)))

(defn reading-find-form
  [pred rdr] {:pre [(instance? java.io.PushbackReader rdr)]}
  (try
    (loop []
      (let [form (doto (read-clj rdr) str)]
        (if (try (pred form)
                 (catch Exception e false)) 
          form
          (recur))))
    (catch Exception e nil)))

(defn read-file-ns-form
  "Attempts to read a (ns ...) or (in-ns ...) declaration from file, and
  returns the first matching unevaluated form.  Returns nil if read
  fails, or no such declaration is found."
  [file]
  (with-open [rdr (java.io.PushbackReader. (io/reader file))]
    (reading-find-form (comp #{'ns 'in-ns} first) rdr)))
