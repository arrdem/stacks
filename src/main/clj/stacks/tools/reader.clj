(ns stacks.tools.reader
  "Helpers for working with readers and reading files."
  {:authors ["Reid \"arrdem\" McKenzie <me@arrdem.com>"]
   :license "https://www.eclipse.org/legal/epl-v10.html"}
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

(defn read-source
  "Read a form from a reader, returning the text for the form read NOT the value of the form read."
  [^java.io.Reader rdr]
  (let [buffer (StringBuilder.)
        pbr    (proxy [java.io.PushbackReader] [rdr]
                 (read []
                   (let [i (proxy-super read)]
                     (.append buffer (char i))
                     i)))]
    (if (= :unknown *read-eval*)
      (throw
       (IllegalStateException.
        "Unable to read source while `clojure.core/*read-eval*` is `:unknown`."))
      (read pbr))
    (.toString buffer)))
