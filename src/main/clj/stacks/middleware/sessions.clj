(ns stacks.middleware.sessions
  "A Stacks server middleware for rendering sessions."
  {:authors ["Reid \"arrdem\" McKenzie <me@arrdem.com>"]
   :license "https://www.eclipse.org/legal/epl-v10.html"})

(defn articles
  "Middleware transformer.

  Accepts a function of a Leiningen project which generates a Stacks content tree.
  Returns a new function of a Leiningen project which will also generate session content."
  [f]
  ;; FIXME (arrdem 2017-12-29):
  ;;   Do something.
  f)