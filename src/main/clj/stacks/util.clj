(ns stacks.util
  "Utilities used in implementing Stacks."
  {:authors ["Reid \"arrdem\" McKenzie <me@arrdem.com>"]
   :license "https://www.eclipse.org/legal/epl-v10.html"}
  (:require [clojure.java.io :as io]
            [stacks.tools.articles :as articles]
            [stacks.tools.namespace :refer [parse-ns-form]]
            [stacks.tools.reader :refer [read-file-ns-form]]
            [detritus :refer [zip]]
            [detritus.types :refer [pattern? uri? file?]]
            [detritus.update :refer [map-vals take-when]]
            [clojure.string :as string])
  (:import [java.net URI]
           [java.io File]))

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

(defn uri-ignored?
  "Given a configuration, return `true` if and only if the given URI matches an ignored pattern."
  [{patterns :ignored-patterns :as config} ^URI uri]
  {:pre [(uri? uri)]}
  (let [uri-str (.toString uri)]
    (boolean (some #(re-find % uri-str) patterns))))

(defn find-path
  "Implementation detail.

  Given a File instance, return a sequence containing only the given File and its children if any."
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
