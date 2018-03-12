(ns stacks.middleware.docstrings
  "A Stacks server middleware for rendering docstrings."
  {:authors ["Reid \"arrdem\" McKenzie <me@arrdem.com>"]
   :license "https://www.eclipse.org/legal/epl-v10.html"})

(def wrap-docstrings
  "Middleware transformer.

  Accepts a function of a Leiningen project which generates a Stacks content tree.
  Returns a new function of a Leiningen project which will also generates docstrings content."
  (fn [stack]
    (fn [project]
      ;; FIXME (arrdem 2017-12-29):
      ;;   Do something.
      (stack project))))
