(ns stacks.tools.pygments
  (:require [clojure.string :as str]
            [me.raynes.conch :refer [let-programs]]))

(defn lexers []
  (let-programs [pygmentize "pygmentize"]
    (->> (pygmentize "-L" "lexers")
         (re-seq #"(?sm)^\* (((, )?[^:,\s]+)+):")
         (mapcat (fn [[_text names]]
                   (str/split names #", "))))))

(defn pygmentize [language input]
  (let-programs [pygmentize "pygmentize"]
    (pygmentize "-fhtml"
                (str "-l" language)
                "-Ostripnl=False,encoding=utf-8"
                {:in input})))

(defn pygmentize-file [^java.io.File input-file]
  (let-programs [pygmentize "pygmentize"]
    (pygmentize "-fhtml"
                "-Ostripnl=False,encoding=utf-8"
                (.getAbsolutePath input-file))))
