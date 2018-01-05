(ns stacks.middleware.util
  "Utilities for writing Stacks middleware."
  {:authors ["Reid \"arrdem\" McKenzie <me@arrdem.com>"]
   :license "https://www.eclipse.org/legal/epl-v10.html"}
  (:require [clojure.java.io :as io]
            [clojure.java.classpath :refer [classpath urls]]
            [stacks.articles :as articles]
            [stacks.tools.namespace :refer [parse-ns-form]]
            [stacks.tools.reader :refer [read-file-ns-form]]
            [detritus :refer [zip]]
            [detritus.types :refer [pattern? uri? file?]]
            [detritus.update :refer [map-vals take-when]]
            [clojure.string :as string]))

(defn normalize-patterns
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

(defn normalize-options
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

(defn find-project-files
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
