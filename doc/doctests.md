# Doctests

["doctests"](https://groups.google.com/forum/#!msg/comp.lang.python/DfzH5Nrt05E/Yyd3s7fPVxwJ) are an old Python idea.

Documentation lives close to the definitions of our functions and so too should the tests.
Some subset of our tests are "interesting" and could be illustrative as examples, so what if we were to put examples in our documentation and also extract them as tests to keep them from going stale?

    ---
    {:namespace user
     :dependencies [[org.clojure "1.6.0"]]
     :as "%"}
    ---
    >> (inc 1)
    => (= % 2)

    >> (inc 1.0)
    => (= % 2.0)

    >> (inc 3/2)
    => (= % 5/2)

Much like [Sessions](/doc/sessions.md), doctests consist of an optional EDN header followed by a sequence of forms.
Unlike sessions which are presumed to only have one "prompt", doctests have three.

* The `>>` prompt (or the `:eval-prompt` option as an option or in the EDN header) is used to denote forms which should be evaluated.
The value of the last evaluated form is bound to `%` (or the `:as` option).
* The `=>` prompt (or the `:is-prompt`) is used to denote forms which should be `clojure.test/is` assertions.
* The `:>` prompt (or the `:valid-prompt`) is used to denote forms which should be `clojure.spec.alpha/valid?` assertions.

Just like sessions, input forms may be followed output and other text before the next prompt.

## Demo: Doctest parsing

Doctests can be parsed to a data structure using `stacks.tools.docttests`

```clj
> (require '[clojure.java.io :as io])
nil
> (require '[stacks.tools.doctests :refer [parse-doctests]])
nil
> (parse-doctests (slurp (io/resource "example.doctest")))
{:type :stacks.tools.doctests/doctests,
 :profile {:prompt ">>|=>|:>",
           :namespace clojure.core,
           :dependencies [[org.clojure "1.6.0"]],
           :eval-prompt ">>",
           :is-prompt "=>",
           :valid-prompt ":>",
           :as "%"},
 :tests ({:comment nil,
          :input "(inc 1)",
          :results "\n",
          :type :stacks.tools.doctests/doctest,
          :assertions ({:type :stacks.tools.doctests/is,
                        :input "(= % 2)",
                        :comment nil})}
         {:comment nil,
          :input "(inc 1.0)",
          :results "\n",
          :type :stacks.tools.doctests/doctest,
          :assertions ({:type :stacks.tools.doctests/is,
                        :input "(= % 2.0)",
                        :comment nil})}
         {:comment nil,
          :input "(inc 3/2)",
          :results "\n",
          :type :stacks.tools.doctests/doctest,
          :assertions ({:type :stacks.tools.doctests/is,
                        :input "(= % 5/2)",
                        :comment nil})})}
>
```

## Demo: Doctest execution

Doctests as data are well and good, but they can also be compiled to test functions.

Continuing the example from above,

```
user> (require '[stacks.tools.doctests :refer [compile-doctests]])
nil
;; The doctest structure from above is *2
user> (compile-doctests *2)
#object[clojure.core$eval20571$fn__20572 "0x7eb09b8" "clojure.core$eval20571$fn__20572@7eb09b8"]
;; Run the compiled doctest function
user> (*1)
true
user>
```

The intent of doctests is that they can be embedded in docstrings.
For instance one could define a function `abs` as such

    (defn abs
      "Return the absolute value of number `x`.

      Works with all numbers supported by the runtime
      and correctly handles integer overflow, unike `Math/abs`.

      ```clj/doctest
      >> (abs -3)
      => 3

      >> (abs -3/5)
      => 3/5

      >> (abs -3.5)
      => 3.5

      >> (abs Integer/MIN_VALUE)
      => 2147483648
      ```
      "
      {:attribution "weavejester/medley"
       :added "0.1"}
      [x]
      (if (neg? x) (- x) x))

## Demo: Doctest runner

Doctests can be searched for and installed for use by the standard `clojure.test` runner.

`clojure.test` recognizes the `^:test` value of any var as a test, so this just searches for doctests, compiles and installs them.
Installing doctests does not overwrite existing `^:test` fns, doctests are chained with other test drivers.

```clj
user> (defn double
        "A function which doubles

        ```clj/doctest
        >> (double 2)
        => (= % 4)
        >> (double 8)
        => (= % 16)
        ```"
        [x]
        (* x 2))
#'user/double
;; Install tests only against the user namespace
user> (stacks.tools.doctest-runner/install-doctests! [#"user"])
Installed doctests on #'user/double
nil
;; Use the normal clojure.test runner
user> (clojure.test/run-tests)

Testing user

Ran 1 tests containing 2 assertions.
0 failures, 0 errors.
{:test 1, :pass 2, :fail 0, :error 0, :type :summary}
user>
```

By default, the doctest runner searches for and installs all tests.
To use it from [circleci.test](https://github.com/circleci/circleci.test) you just have to run `install-doctests!` in your `config.clj`.

## Doctest limitations

Doctests are **absolutely not** a replacement for your integration test suite.
The role of doctests is to be terse executable source of examples for your documentation.
As such, doctests should be simple, used small readable inputs and illustrate the general behavior of a function.

If your function needs fixtures, meaningful configuration data or other context they probably aren't appropriate.
