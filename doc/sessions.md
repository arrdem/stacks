# Sessions

Right now, this is just a sketch at a `.repl` file extension for writing examples.

A `.repl` file consists of a single `---` fenced EDN form much like a YAML header which may specify metadata about the examples in the file.
For instance, the `:prompt` keyword may be a string or pattern identifying the REPL prompt present in the examples.
Dependencies used in the REPL session may be specified with a Leiningen style `:dependencies` vector.
The namespace used may also be specified with the `:namespace` key.

[**example.repl**](/src/test/resources/example.repl):
```
---
{:namespace    user
 :dependencies [[org.clojure/clojure "1.8.0"]]
 :prompt       "user>"
}
---
user> (+ 1 1)
1
;; Some comment
user> (conj #{:foo :bar} :baz)
#{:foo :bar :baz}
;; That's all folks!
user> ^d
```

Example files can be loaded into a data structure, describing the example as a whole and each given `(input, output)` pair.

### Demo: Session parsing

For instance, in one version of stacks the above file loaded to the data structure

```clj
stacks.session> (parse-session (slurp "example.repl"))
{:tag     :stacks.sessions/session,
 :profile {:prompt "user>",
           :namespace user,
           :dependencies [[org.clojure/clojure "1.8.0"]]},
 :pairs   ({:tag     :stacks.sessions/pair,
            :comment nil,
            :input   "(+ 1 1)",
            :results "1"}
           {:tag     :stacks.sessions/pair,
            :comment ";; Some comment\n",
            :input   "(conj #{:foo :bar} :baz)",
            :results "#{:foo :bar :baz}"})}
```

From this structure and given the namespace in which the session occurs it would be possible to use `clojure.tools.analyzer.jvm` to perform expression analysis & macroexpansion for full syntax highlighting.
There are some challenges there because of how tools.analyzer treats inlinable expressions, but using tools.analyzer would allow for much fuller syntax highlighting and the automatic cross-linking of forms with their documentation.
