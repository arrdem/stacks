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

Much like [Sessions](sessions.md), doctests consist of an optional EDN header followed by a sequence of forms.
Unlike sessions which are presumed to only have one "prompt", doctests have three.

The `>>` prompt (or the `:eval-prompt` option as an option or in the EDN header) is used to denote forms which should be evaluated.
The value of the last evaluated form is bound to `%` (or the `:as` option).
The `=>` prompt (or the `:is-prompt`) is used to denote forms which should be `clojure.test/is` assertions.
The `:>` prompt (or the `:valid-prompt`) is used to denote forms which should be `clojure.spec.alpha/valid?` assertions.

Just like sessions, input forms may be followed output and other text before the next prompt.

## Demo: Doctest parsing

Doctests can be parsed to a data structure using `stacks.tools.docttests`

```clj
user> (require '[clojure.java.io :as io])
nil
user> (require '[stacks.tools.doctests :refer [parse-doctests]])
nil
user> (parse-doctests (slurp (io/resource "example.doctest")))
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
user> 
```

## Demo: Doctest running

Doctests as data are well and good, but they can also be compiled to test functions.

Continuing the example from above,

```
user> (require '[stacks.tools.doctests :refer [compile]])
WARNING: compile already refers to: #'clojure.core/compile in namespace: user, being replaced by: #'stacks.tools.doctests/compile
nil
;; The doctest structure from above is *2
user> (compile *2)
#object[clojure.core$eval20571$fn__20572 "0x7eb09b8" "clojure.core$eval20571$fn__20572@7eb09b8"]
;; Run the compiled doctest function
user> (*1)
true
user> 
```
