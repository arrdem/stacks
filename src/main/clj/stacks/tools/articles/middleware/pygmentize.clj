(ns stacks.tools.articles.middleware.pygmentize
  "Article middleware for pygmentizing code blocks."
  (:require [stacks.tools.articles :refer [handle-render-block]]
            [stacks.tools.pygments :refer [lexers pygmentize]]))

(defonce +known-lexers+
  (delay (set (pygments-lexers))))

(defn handle-render-pygmentize
  "Article middleware.

  Handle most code blocks by pygmentizing the content."
  [middleware]
  (fn [{:keys [tag attrs raw] :as node}]
    (if (and (Boolean/parseBoolean (get attrs :highlight "true"))
             (contains? @+known-lexers+ tag))
      (pygmentize tag raw)
      (middleware node))))

(comment
  ;; clj is known to pygments
  ((render-with-pygmentize handle-render-block)
   {:type :shelving.tools.articles/code
    :tag "clj"
    :attrs {}
    :raw "(def foo 1)\n"})

  ;; foo is not
  ((render-with-pygmentize handle-render-block)
   {:type :shelving.tools.articles/code
    :tag "foo"
    :attrs {}
    :raw "foo bar baz qux\n"}))
