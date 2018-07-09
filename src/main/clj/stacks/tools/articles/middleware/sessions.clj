(ns stacks.tools.articles.middleware.sessions
  "Article middleware for parsing and rendering REPL sessions."
  (:require [stacks.tools.sessions
             :as sessions
             :refer [parse-session evaluate-session render-session]]))

(defonce +session-tags+
  #{"clj+session"
    "clojure+session"
    "cljs+session"
    "clojurescript+session"})

(defn handle-parse-sessions
  "Article parser middleware for handling Stacks sessions."
  [middleware]
  (fn [tag attrs raw]
    (if (contains? +session-tags+ tag)
      (parse-session raw)
      (middleware tag attrs raw))))

(defn handle-render-sessions
  "Article rendering middleware for handling Stacks sessions."
  [middleware]
  (fn [{:keys [type] :as node}]
    (if (= type ::sessions/session)
      (as-> node %
        (evaluate-session %)
        (render-session %))
      (middleware node))))
