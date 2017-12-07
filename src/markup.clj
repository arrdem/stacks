(ns examples
  (:require [clojure.java.io :as io] 
            [clojure.tools.reader.reader-types :refer [read-char]]
            [rewrite-clj.parser :as p]
            [rewrite-clj.reader :refer [string-reader]])
  (:import java.io.Reader))

(defn read-prompt-prefixed-form []
  
  )

(def header-regex
  #"^---\n((.*\n)*?)---\n((.*\n)+)")

(defn merge-profiles [& profiles])

(defn slurp-reader [rdr]
  (loop [buff (java.lang.StringBuilder.)]
    (let [c (read-char rdr)]
      (if c
        (do (.append buff c)
            (recur buff))
        (.trim (.toString buff))))))

(defn parse-examples [{:keys [prompt]} buffer]
  (let [pattern (re-pattern (format "(?sm)(%s\\s*)(.*?)(?=%s)"
                                    prompt prompt))]
    (->> (re-seq pattern buffer)
         (map (fn [[_match _prompt text _prompt?]]
                (let [reader (string-reader text)
                      form   (p/parse reader)
                      text   (slurp-reader reader)]
                  {:tag  ::example
                   :form (str form)
                   :tail text}))))))

(defn parse-session
  ([text]
   (parse-session
    {:prompt       "^[^>]*?> "
     :namespace    'user
     :dependencies '[[org.clojure/clojure "1.8.0"]]}
    text))
  ([profile text]
   (let [[_match kvs _ body _] (re-find header-regex text)
         example-profile       (read-string kvs)
         ;;profile               (merge-profiles profile example-profile)
         ]
     {:tag      ::session
      :profile  profile
      :examples (parse-examples example-profile body)})))
