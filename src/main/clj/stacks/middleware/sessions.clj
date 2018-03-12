(ns stacks.middleware.sessions
  "A Stacks server middleware for rendering sessions."
  {:authors ["Reid \"arrdem\" McKenzie <me@arrdem.com>"]
   :license "https://www.eclipse.org/legal/epl-v10.html"})

(def wrap-sessions
  "Middleware transformer.

  Accepts a function of a Leiningen project which generates a Stacks content tree.
  Returns a new function of a Leiningen project which will also generate session content."
  (fn [stack]
    (fn [project]
      ;; FIXME (arrdem 2017-12-29):
      ;;   Do something.
      (stack project))))
