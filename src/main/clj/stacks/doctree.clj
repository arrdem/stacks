(ns stacks.doctree
  "Trees of sources and docs.

  Articles don't come alone.
  They're the most useful when they come in groups.
  Documentation engines need to work in terms of large filesets of documentation and sources."
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
            [clojure.string :as string])
  (:import [java.io File]
           [java.net URI]))

(set! *warn-on-reflection* true)

(def default-options
  {:source-extensions  #{".clj" ".cljc"}
   :session-extensions #{".repl"}
   :ignored-patterns   #{#"checkouts"}})

(def find-sources
  (partial find-project-files :source-extensions [:source-paths] :sources))

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
  a classpath, indexing all"
  [options project]
  (let [options       (normalize-options options)
        project-files (find-files options project)]
    (index-project options project project-files)))
