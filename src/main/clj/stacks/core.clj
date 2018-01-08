(ns stacks.core
  (:require [stacks.doctree :as doctree]
            [clojure.core.match :refer [match]]))

(def default-options
  {:ignored-patterns #{"//checkouts"}})

(defn wrap-files
  "Default Stacks project middleware, intended to be the root of a middleware stack.

  Recognizes any file which was not handled by a previous middleware,
  indexing it as a \"default file\", which cannot be meaningfully
  analyzed and has no content which can be identified other than by
  the URL's path with respect to the root of the project.

  ## Usage
  
  ```clj
  (let [index-handler (-> (wrap-files)
                          (wrap-my-file-type)
                          (wrap-project-index))
        http-handler  (-> )]
    
  ```
  "
  []
  (fn [project global-options state request]
    (match request
      {:verb ::discover :url url}
      ,,[state
         {:type ::default-file
          :url  url}]

      {:verb ::index :url url}
      ,,[state
         {:type ::default-file
          :url  url}])))

(defn index-project
  "Middleware runner for building & updating project indices.
  
  Returns an index state structure, and the computed index. The state
  should be retained for re-use when re-indexing changed files."
  [middleware project options]
  (let [[state files] (middleware project options {})]))
