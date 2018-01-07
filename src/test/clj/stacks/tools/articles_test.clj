(ns stacks.tools.articles-test
  (:require [stacks.tools.articles :as a]
            [clojure.test :as t]
            [clojure.java.io :as io]))

(t/deftest parser-test
  (t/testing "Testing the root example"
    (t/is (a/markdown->article (slurp (io/resource "example.md"))))))
