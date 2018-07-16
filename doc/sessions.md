# Sessions

Much Clojure documentation comes in the form of a REPL session.
Unlike Java and other languages where examples must be given wholesale in the form of code, inputs, a build invocation and some terminal output REPLs naturally capture experimentation and consequence.

As the name suggests a REPL (Read - Eval - Print - Loop) session is a concatenation of many formats of text.
There's usually a prompt such as `user>` or `foo=>` or even `λ>` used to indicate that input is awaited.
There's a form in full Clojure / EDN which is read and evaluated.
Evaluation may produce a whole lot of output eg.
via printing or other means, or simple warnings from the runtime.
Finally the result of evaluation is itself printed, and the next prompt occurs.

Ideally, we'd be able to keep all these parts of text straight.
Syntax highlighting the evaluated form in its evaluation environment may be valuable.
Likewise syntax highlighting the result of evaluation may be valuable.
Perhaps other printed output has some interpretation that's worth formatting.

By using the input prompt as a delimiter, and understanding that `clojure.core/read` is an authoritative on what constitutes the input form following the prompt, we can at least recover the prompt and the result(s) of evaluation.
Determining what of the output is last and the result is more difficult, but possible.

Stacks provides support for parsing sessions, for which I shall use the file extension `.repl`.
`.repl` files consists of a single `---` fenced EDN form - much like a YAML header - which may specify options regarding the interpretation of the session.

For instance, the `:prompt` keyword may be a string or pattern identifying the REPL prompt present in the examples.
Dependencies used in the REPL session may be specified with a Leiningen style `:dependencies` vector.
The namespace context in which reading and evaluation occurs may also be specified with the `:namespace` key.

[**example.repl**](/src/test/resources/example.repl):
```
---
{:namespace user}
---
user> (+ 1 1)
1
user> (conj #{:foo :bar} :baz)
#{:foo :bar :baz}
user> ^d
```

Example files can be loaded into a data structure, describing the example as a whole and each given `(input, output)` pair.

### Demo: Session parsing

For instance, in one version of stacks the above file loaded to the data structure

```clj+session
---
{:namespace user
 :eval true}
---
user> (stacks.tools.sessions/parse-session
        (slurp (clojure.java.io/resource "example.repl")))
```

From this structure and given the namespace in which the session occurs it would be possible to use `clojure.tools.analyzer.jvm` to perform expression analysis & macroexpansion for full syntax highlighting.
There are some challenges there because of how tools.analyzer treats inlinable expressions, but using tools.analyzer would allow for much fuller syntax highlighting and the automatic cross-linking of forms with their documentation.
