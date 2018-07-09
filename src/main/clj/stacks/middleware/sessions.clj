(ns stacks.middleware.sessions
  "A Stacks server middleware for rendering sessions."
  {:authors ["Reid \"arrdem\" McKenzie <me@arrdem.com>"]
   :license "https://www.eclipse.org/legal/epl-v10.html"})

(defn parse-session [tag attrs raw]
  (if (Boolean/parseBoolean (get attrs :render "false"))
    (do (require 'stacks.tools.sessions)
        (as-> raw %
          ((resolve 'stacks.tools.sessions/parse-session) %)))
    (parse-default tag attrs raw)))
