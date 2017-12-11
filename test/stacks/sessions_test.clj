(ns stacks.sessions-test
  (:require [stacks.sessions :as s]
            [clojure.test :as t]
            [clojure.java.io :as io]))

(t/deftest parser-test
  (t/testing "Testing the root example"
    (t/is (s/parse-session (slurp "example.repl"))))

  (t/testing "Testing various forms of syntax errors"
    (t/are [example]
        (let [res (io/resource example)]
          (t/is res)
          (t/is (s/parse-session (slurp res))))

      ;;--------------------
      "test-no-header.repl"
      "test-just-header.repl"
      "test-exception-body.repl"
      "test-incomplete-form.repl")))
