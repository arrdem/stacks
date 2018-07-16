(ns stacks.tools.sessions-test
  (:require [stacks.tools.articles :as a]
            [stacks.tools.sessions :as s]
            [stacks.tools.prepl :as p]
            [stacks.tools.articles.middleware.sessions :as sm]
            [clojure.test :as t]
            [clojure.java.io :as io]))

(t/deftest parser-test
  (t/testing "Testing the root example"
    (t/is (s/parse-session (slurp (io/resource "example.repl")))))

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

(t/deftest eval-test
  (t/testing "Can parse and evaluate the multi-session demo like we expect"
    (t/is (= (->> (io/resource "multi-session-test.md")
                  (a/parse-article (sm/handle-parse-sessions a/handle-parse-block))
                  (:content)
                  (filter #(= (:type %) ::s/session))
                  (mapv s/evaluate-session)
                  (mapv (fn [session]
                          (-> session
                              (update :profile #(select-keys % [:session]))
                              (update :pairs
                                      (fn [pairs]
                                        (mapv (fn [pair]
                                                (update pair :results
                                                        #(vec (remove (comp #{::p/bindings} :type) %))))
                                              pairs)))))))
             [{:type :stacks.tools.sessions/session,
               :profile {:session "session-1"},
               :pairs [{:type :stacks.tools.sessions/pair,
                        :namespace "user",
                        :prompt ">",
                        :comment nil,
                        :input "(+ 1 1)",
                        :results [{:type :stacks.tools.prepl/ret,
                                   :val 2,
                                   :ns "user",
                                   :ms 0,
                                   :form "(+ 1 1)",
                                   :form-id 0,
                                   :stacks.tools.sessions/val "2\n"}]}]}
              {:type :stacks.tools.sessions/session,
               :profile {:session "session-2"},
               :pairs [{:type :stacks.tools.sessions/pair,
                        :namespace "user",
                        :prompt ">",
                        :comment nil,
                        :input "(+ 2 2)",
                        :results [{:type :stacks.tools.prepl/ret,
                                   :val 4,
                                   :ns "user",
                                   :ms 0,
                                   :form "(+ 2 2)",
                                   :form-id 0,
                                   :stacks.tools.sessions/val "4\n"}]}]}
              {:type :stacks.tools.sessions/session,
               :profile {:session "session-1"},
               :pairs [{:type :stacks.tools.sessions/pair,
                        :namespace "user",
                        :prompt ">",
                        :comment nil,
                        :input "*1",
                        :results [{:type :stacks.tools.prepl/ret,
                                   :val 2,
                                   :ns "user",
                                   :ms 0,
                                   :form "*1",
                                   :form-id 0,
                                   :stacks.tools.sessions/val "2\n"}]}]}
              {:type :stacks.tools.sessions/session,
               :profile {:session "session-2"},
               :pairs [{:type :stacks.tools.sessions/pair,
                        :namespace "user",
                        :prompt ">",
                        :comment nil,
                        :input "*1",
                        :results [{:type :stacks.tools.prepl/ret,
                                   :val 4,
                                   :ns "user",
                                   :ms 0,
                                   :form "*1",
                                   :form-id 0,
                                   :stacks.tools.sessions/val "4\n"}]}]}]))))
