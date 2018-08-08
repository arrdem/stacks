(ns stacks.tools.articles.middleware.graphviz
  "Article middleware for rendering graphvivz."
  (:require [me.raynes.conch :refer [let-programs]]))

(defonce +graphviz-tags+
  #{"dot"
    "graphviz"})

(defn handle-render-graphviz
  "Handle graphviz by compiling it ... to inline image data I guess?

  Unless render=false."
  [middleware]
  (fn [{:keys [tag attrs raw] :as node}]
    (if (contains? +graphviz-tags+ tag)
      (let-programs [graphviz "dot"]
        [:div.center
         (graphviz "-Tsvg"
                   {:in raw})])
      (middleware node))))
