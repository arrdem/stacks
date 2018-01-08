(ns stacks.tools.doctests-test
  (:require [stacks.tools.doctests :as dt]
            [clojure.test :as t]
            [clojure.java.io :as io]))

(t/deftest parser-test
  (let [doctests? (dt/parse-doctests (slurp (io/resource "example.doctest")))]
    (t/is doctests?)

    (doseq [t (:tests doctests?)]
      (t/is (:input t))
      (t/testing (format "Testing that %s has populated assertion forms" t)
        (doseq [a (:assertions t)]
          (t/is (:input a)))))

    ((dt/compile-doctests doctests?))))
