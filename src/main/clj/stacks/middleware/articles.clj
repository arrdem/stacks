(ns stacks.middleware.articles
  "A Stacks server middleware for rendering articles."
  {:authors ["Reid \"arrdem\" McKenzie <me@arrdem.com>"]
   :license "https://www.eclipse.org/legal/epl-v10.html"}
  (:require [stacks.middleware.util :refer [find-project-files]]
            [stacks.tools.articles :as articles]))

(def default-options
  {:article-extensions #{".md" ".markdown"}})

(def find-articles
  (partial find-project-files :doc-extensions [:doc-paths] :articles))

(defn index-articles [options project project-files]
  (let [{:keys [articles]} project-files]
    (map articles/markdown->article articles)))

#_(defn wrap-articles
    "Middleware transformer.

  Accepts a function of a Leiningen project which generates a Stacks content tree.
  Returns a new function of a Leiningen project which will also generate article content."
    ([f]
     (wrap-articles f default-options))
    ([f options]
     ;; FIXME (arrdem 2017-12-29):
     ;;   Do something.
     (fn [global-options project]
       (-> (index-articles (merge global-options options) project) (f)))))
