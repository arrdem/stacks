(ns stacks.middleware.articles
  "A Stacks server middleware for rendering articles."
  {:authors ["Reid \"arrdem\" McKenzie <me@arrdem.com>"]
   :license "https://www.eclipse.org/legal/epl-v10.html"}
  (:require [clojure.core.match :refer [match]]
            [stacks.middleware.util :refer [find-project-files]]
            [stacks.tools.articles :as articles]))

(def default-options
  {:article-extensions #{".md" ".markdown"}})

(def find-articles
  (partial find-project-files :doc-extensions [:doc-paths] :articles))

(defn index-articles [options project project-files]
  (let [{:keys [articles]} project-files]
    (map articles/markdown->article articles)))

(defn wrap-articles
  "Middleware transformer.

  Accepts a function of a Leiningen project which generates a Stacks content tree.
  Returns a new function of a Leiningen project which will also generate article content."
  ([f]
   (wrap-articles f default-options))
  ([f options]
   (fn [global-options project request]
     (match request
       {:type })

     )))
