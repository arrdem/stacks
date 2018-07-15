(ns stacks.tools.articles-test
  (:require [stacks.tools.articles :as a]
            [clojure.test :as t]
            [clojure.java.io :as io]))

(t/deftest parser-test
  (t/testing "Testing the root example"
    (t/is (a/parse-article a/handle-parse-block
                           (io/resource "example.md"))))

  (t/testing "Testing the README"
    (t/is (a/parse-article a/handle-parse-block
                           (io/file "README.md"))))

  (t/testing "Testing the articles document"
    (t/is (a/parse-article a/handle-parse-block
                           (io/file "doc/articles.md"))))

  (t/testing "Testing the doctests document"
    (t/is (a/parse-article a/handle-parse-block
                           (io/file "doc/doctests.md"))))

  (t/testing "Testing the projects document"
    (t/is (a/parse-article a/handle-parse-block
                           (io/file "doc/projects.md"))))

  (t/testing "Testing the sessions document"
    (t/is (a/parse-article a/handle-parse-block
                           (io/file "doc/sessions.md")))))
