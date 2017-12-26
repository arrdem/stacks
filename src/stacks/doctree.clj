(ns stacks.doctree
  "Trees of sources and docs.

  Articles don't come alone.
  They're the most useful when they come in groups.
  Documentation engines need to work in terms of large filesets of documentation and sources."
  (:require [clojure.java.io :as io]
            [clojure.java.classpath :refer [classpath urls]]
            [stacks.articles :as articles]
            [stacks.tools.namespace :refer [parse-ns-form]]
            [stacks.tools.reader :refer [read-file-ns-form]]
            [detritus :refer [zip]]
            [detritus.types :refer [pattern? uri? file?]]
            [detritus.update :refer [map-vals take-when]]
            [clojure.string :as string])
  (:import [java.io File]
           [java.net URI]))

(set! *warn-on-reflection* true)

(def default-options
  {:doc-extensions     #{".md" ".markdown"}
   :source-extensions  #{".clj" ".cljc"}
   :session-extensions #{".repl"}
   :ignored-patterns   #{#"checkouts"}})

(defn- normalize-patterns
  "Implementation detail.

  Used to normalize ignored URI patterns to patterns."
  [patterns?]
  (->> patterns?
       (map (fn [p] (if (string? p)
                      (re-pattern (format ".*?%s$" (string/escape p {\. "\\."})))
                      (if (pattern? p)
                        p
                        (throw (IllegalArgumentException.
                                (format "Cannot normalize type %s to java.util.regex.Pattern"
                                        (class p))))))))
       (into #{})))

(defn- normalize-options
  "Implementation detail.

  Used to normalize options before we try and work with them."
  [options]
  (-> options
      (update :ignored-patterns normalize-patterns)
      (update :source-extensions normalize-patterns)
      (update :session-extensions normalize-patterns)
      (update :doc-extensions normalize-patterns)
      (assoc :type ::options)))

(defn uri-ignored?
  "Given a configuration, return `true` if and only if the given URI matches an ignored pattern."
  [{patterns :ignored-patterns :as config} ^URI uri]
  {:pre [(uri? uri)]}
  (let [uri-str (.toString uri)]
    (boolean (some #(re-find % uri-str) patterns))))

(defn find-path
  "Implementation detail.

  Given a File instance, a sequence containing only the given File and its children if any."
  [options cache ^File path]
  (when-not (uri-ignored? options (.toURI path))
    (or (get @cache path)
        (let [r (when (.exists path)
                  (cons path
                        (if (.isDirectory path)
                          (mapcat (fn [^File entry]
                                    (if (.isDirectory entry)
                                      (find-path options cache entry)
                                      [entry]))
                                  (map (partial io/file path)
                                       (.list path))))))]
          (vswap! cache assoc path r)
          r))))

(defn- find-project-files
  "Implementation detail.

  Finds one kind of files in the project structure and indexes them,
  using a cache to minimize directory traversal.

  Recursively walks all directories listed as source, resource or doc
  paths, returning sequence of structure all located files and directories.

  Files matching any of the configured `:ignored-patterns` are ignored."
  [filter-k path-ks result-k options project cache]
  (let [filter-patterns (get options filter-k)
        paths           (mapcat project path-ks)]
    {result-k (->> paths
                   (map #(if-not (file? %) (io/file %) %))
                   (mapcat (partial find-path options cache))
                   (keep (fn [^File f]
                           (let [uri      ^URI (.toURI f)
                                 uri-text (.toString uri)]
                             (when (some #(re-matches % uri-text) filter-patterns)
                               uri)))))}))

(def find-sources
  (partial find-project-files :source-extensions [:source-paths] :sources))

(def find-docs
  (partial find-project-files :doc-extensions [:doc-paths] :docs))

(def find-sessions
  (partial find-project-files :session-extensions [:session-paths] :sessions))

;; FIXME (arrdem 2017-12-23):
;;   find-* should get its own namespace
(defn find-files
  "Implementation detail.

  Returns a `::fileset` structured representation of all the
  recognized source, documentation and session files in the project.

  Sources, documentation and sessions are required to be set-wise
  distinct. If a file matches more than one set of file patterns, an
  `IllegalStateException` will be thrown."
  [options project]
  (let [cache (volatile! {})]
    (merge {:type ::fileset}
           (find-sources options project cache)
           (find-docs options project cache)
           (find-sessions options project cache))))

;; FIXME (arrdem 2017-12-23):
;;   Ran out of time on the flight.
(declare index-docs index-sessions)

(defn index-sources [options project project-files]
  (let [{:keys [platform]
         :or   {platform 'clj}} project
        {:keys [sources]}       project-files]
    (map (fn [url]
           {:type      ::file
            :namespace (if-let [form (read-file-ns-form url)]
                         (if (= (first form) 'in-ns)
                           ;; FIXME (arrdem 2017-12-25):
                           ;;   What kind of ns structure to generate in this case?
                           (second form)
                           (parse-ns-form form)))
            ;; FIXME (arrdem 2017-12-25):
            ;;   I really want to index the contents of a namespace too. This means doing code
            ;;   loading because that's the only way to actually extract docstrings. Code loading
            ;;   has to happen both in a sandbox and in a subprocess because of potential dependnecy
            ;;   conflicts between whatever process is doing the indexing and the target.
            ;;
            ;;   Booting JVMs is freaking slow. Maybe we don't care, and maybe it's acceptable for
            ;;   stacks to sit in-process as a REPL sidecar of some description. But being able to
            ;;   do well constrained code loading in a sandbox for generating static docs is
            ;;   desirable.
            ;;
            ;; Prior art: Boot pods
            ;;   https://github.com/boot-clj/boot/wiki/Pods
            :url       url})
         sources)))

(defn index-project
  "Given Options, a Leiningen project and the files comprising that
  project analyzes & indexes them for rendering."
  [options project project-files]
  (merge {:type ::index}
         (index-sources options project project-files)
         (index-docs options project project-files)
         (index-sessions options project project-files)))

(defn project->doctree
  "Options, Leiningen project -> doctree structure which can be compiled, tested or checked.

  Accepts a Leiningen style project, having `:source-paths`,
  `:java-source-paths`, `:resource-paths`, and `:doc-paths`. Emulates
  a classpath, indexing all "
  [options project]
  (let [options       (normalize-options options)
        project-files (find-files options project)]
    (index-project options project project-files)))
