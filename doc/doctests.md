# Doctests

["doctests"](https://groups.google.com/forum/#!msg/comp.lang.python/DfzH5Nrt05E/Yyd3s7fPVxwJ) are an old Python idea.

Documentation lives close to the definitions of our functions and so too should the tests.
Some subset of our tests are "interesting" and could be illustrative as examples, so what if we were to put examples in our documentation and also extract them as tests to keep them from going stale?

    ---
    {:namespace user
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
* The `:>` prompt (or the `:valid-prompt`) is used to denote forms which should be `clojure.spec(.alpha)/valid?` assertions.

Just like sessions, input forms may be followed output and other text before the next prompt.

## Demo: Doctest parsing

Doctests can be parsed to a data structure using `stacks.tools.docttests`

```clj+session
---
{:session "doctests-demo"
 :eval true}
---
> (require '[clojure.java.io :as io])
> (require '[stacks.tools.doctests :refer [parse-doctests]])
> (def *doctests (parse-doctests (slurp (io/resource "example.doctest"))))
> *doctests
```

## Demo: Doctest execution

Doctests as data are well and good, but they can also be compiled to test functions.

Continuing the example from above,

```clj+session
---
{:session "doctests-demo"}
---
> (require '[stacks.tools.doctests :refer [compile-doctests]])
;; The doctest structure from above is *2
> (compile-doctests *doctests)
;; Run the compiled doctest function
> (*1)
true
>
```

The intent of doctests is that they can be embedded in docstrings.
For instance one could define a function `double` as such:

```clj
(defn double
   "A function which doubles
   ```clj+doctest
   >> (double 2)
   => (= % 4)
   >> (double 8)
   => (= % 16)
   ```"
   [x]
   (* x 2))
```

## Demo: Doctest runner

Doctests can be searched for and installed for use by the standard `clojure.test` runner.

`clojure.test` recognizes the `^:test` value of any var as a test, so this just searches for doctests, compiles and installs them.
Installing doctests does not overwrite existing `^:test` fns, doctests are chained with other test drivers.

```clj+session
---
{:session "doctests-demo"}
---
;; Define our double function from above
> (defn double
     "A function which doubles
     ```clj+doctest
     >> (double 2)
     => (= % 4)
     >> (double 8)
     => (= % 16)
     ```"
     [x]
     (* x 2))
;; Install tests only against the user namespace
> (require '[stacks.tools.doctest-runner :refer [install-doctests!]])
> (install-doctests! [#"user"])
;; Use the normal clojure.test runner
> (clojure.test/run-tests)
>
```

By default, the doctest runner searches for and installs all tests.
To use it from [circleci.test](https://github.com/circleci/circleci.test) you just have to run `install-doctests!` in your `config.clj`.

## Doctest limitations

Doctests are **absolutely not** a replacement for your integration test suite.
The role of doctests is to be terse executable source of examples for your documentation.
As such, doctests should be simple, used small readable inputs and illustrate the general behavior of a function.

If your function needs fixtures, meaningful configuration data or other context they probably aren't appropriate.
