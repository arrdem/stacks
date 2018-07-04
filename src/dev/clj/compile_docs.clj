(ns compile-docs
  "A quick hack for rebuilding docs containing manually situated var references.

  This file is part of the Stacks project, copied into the ledger for convenience."
  {:author  ["Reid 'arrdem' McKenzie <me@arrdem.com>"]
   :license "https://www.eclipse.org/legal/epl-v10.html"
   :source  "https://github.com/arrdem/stacks"}
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.tools.logging :as log])
  (:import java.io.File
           java.nio.file.Path))

(def var-doc-pattern
  #"(?ms)^(?<heading>#{2,}) \[(?<name>[^\]]*?)\]\(((?<path>[^\#]*?)#L(?<line>\d+))?\)\n(?<body>.*?)((^<!-- stacks end -->\n\n)|(?=^#{2,})|\Z)")

(def var-heading-pattern
  #"(?ms)^#{2,} \[(?<name>[^\]]+?)\]")

(def var-quote-pattern
  #"(?ms)\[?`#'(?<name>[^\`]+?)`(\]\(.*?\))?")

(defn ensure-trailing-newline [s]
  (if-not (.endsWith s "\n")
    (str s "\n") s))

(defn as-file [file-or-string]
  (if (instance? java.io.File file-or-string)
    file-or-string
    (io/file file-or-string)))

(defn relativize-path
  "Returns a relative path from the directory containing `document` to the `source` location."
  [document source]
  (if (not= document source)
    (.relativize (.getParent (.toPath (as-file document)))
                 (.toPath (as-file source)))
    ""))

(defn var-key [^clojure.lang.Var var]
  (format "%s/%s" (.name (.ns var)) (.sym var)))

(defn strip-leading-space
  "Docstrings are hard to format in part because sometimes they have a
  whole lot of leading whitespace.

  This function tries to take an arbitrary docstring and chomp off the
  minimum amount of COMMON whitespace from every line."
  [docstring]
  (as-> docstring %
    ;; Delete any leading whitespace and newlines
    (str/replace % #"\A[\s\n\r]+" "")
    (str/split-lines %)
    (rest %) ;; First line is always different
    ;; Count leading whitespace
    (keep (fn [l]
            (if-let [match (re-find #"^\s+" l)]
              (count match)))
          %)
    ;; Common is the minimal leading substring
    (apply min 1000 %)
    (format "(?ms)^[\\s&&[^\n\r]]{%s}" %)
    (re-pattern %)
    (str/replace docstring % "")))

(defn document-var [^clojure.lang.Var v ^File doc-file heading links-map]
  (binding [*ns* (.ns v)]
    (let [{:keys [categories arglists doc stability line file]
           :as   var-meta} (meta v)]
      (with-out-str
        (printf "%s [%s/%s](%s#L%s)\n<!-- stacks start - Don't edit this section, it's generated -->\n"
                heading
                (ns-name (.ns v)) (str/replace (name (.sym v)) #"\*" "\\\\*")
                (relativize-path doc-file (.getFile (io/resource file)))
                line)

        (doseq [params arglists]
          (printf " - `%s`\n" (cons (.sym v) params)))

        (printf "\n")

        (when (= stability :stability/unstable)
          (printf "**UNSTABLE**: This API will probably change in the future\n\n"))

        (printf (ensure-trailing-newline
                 (-> doc
                     (strip-leading-space)
                     (str/replace var-quote-pattern
                                  (fn [[match name]]
                                    (if-let [entry (->> (symbol name)
                                                        (ns-resolve *ns*)
                                                        (var-key)
                                                        (get links-map))]
                                      (let [[link-file heading] entry]
                                        (format "[`%s`](%s#%s)"
                                                name
                                                (relativize-path doc-file link-file)
                                                heading))
                                      match))))))

        (printf "<!-- stacks end -->\n\n")))))

(defn recompile-docs [files]
  (doseq [f files]
    (when (.contains (.getCanonicalPath f) "#")
      (.delete f)))

  (let [fcache
        (->> (map (juxt identity slurp) files)
             (into {}))

        links-map
        (reduce (fn [acc f]
                  (reduce (fn [acc [_ name]]
                            (assoc acc name [(.getCanonicalFile f)
                                             (str/replace name #"[./?]" "")]))
                          acc (re-seq var-heading-pattern (get fcache f))))
                {} files)]

    (doseq [f files]
      (try (let [buff (get fcache f)
                 buff* (-> buff
                           (str/replace var-doc-pattern
                                        (fn [[original heading name _ path line _body :as match]]
                                          (try (let [name (str/replace name #"\\\*" "*")
                                                     sym (symbol name)]
                                                 (require (symbol (namespace sym)))
                                                 (-> sym
                                                     resolve
                                                     (document-var f heading links-map)))
                                               (catch Exception e
                                                 (log/fatal e)
                                                 original))))
                           (str/replace var-quote-pattern
                                        (fn [[original name suffix?]]
                                          (if-let [[target-file header] (get links-map name)]
                                            (format "[`#'%s`](%s#%s)" name
                                                    (if (not= f target-file)
                                                      (relativize-path f target-file)
                                                      "")
                                                    header)
                                            (do (log/warnf "%s: Couldn't find a link for %s!" f name)
                                                original))))
                           (str/replace #"\n{2,}\Z" "\n"))]
             (when  (not= buff buff*)
               (log/infof "Rebuilt %s" f)
               (spit f buff*)))

           (catch Exception e
             (log/infof "Encountered error while updating %s:\n%s" f e))))))

(defn recompile-docs!
  "Entry point suitable for a lein alias. Usable for automating doc rebuilding."
  [& files-or-dirs]
  (recompile-docs
   (map #(.getCanonicalFile %)
        (filter #(.endsWith (.getPath ^File %) ".md")
                (mapcat (fn [file-or-dir]
                          (let [f (io/file file-or-dir)]
                            (if (.isDirectory f)
                              (file-seq f)
                              [f])))
                        files-or-dirs)))))
