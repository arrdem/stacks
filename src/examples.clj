(ns markup
  (:require [instaparse.core :refer [parser]]
            [clojure.java.io :as io]))

(def -parser
  (parser (slurp (io/resource "markup.insta"))))
