# Somewhere in the Stacks
<img align="right" src="https://github.com/arrdem/stacks/raw/master/etc/stacks.jpg"/>

**TL;DR** A sketch at a longer-form documentation tool for Clojure.

As a student at the University of Texas at Austin, I loved wandering around the computer science collection of the Perry Castañeda library.
It was always quiet, the musty smell of long-untouched books was great and I could wander from shelf to shelf picking all manner of CS arcana and detail about long-obsolete and forgotten systems to peruse.

It's no secret that most documentation is bad.
Writing good documentation is really hard, and is itself a skill worthy of far more development and attention than most give it myself included.
At best, most of the software I encounter especially in the Clojure space has a README which may contain some examples, docstrings and maybe an illuminating comment or two somewhere.
A little google searching may uncover some examples, but by a large I find that there's really no way to approach the majority of Clojure code but to clone it down and spend an hour or two reading it all.

In writing [Grimoire](http://conj.io), I kept banging back into wanting to write documentation for topics such as destructuring or particular interfaces like sequences which had no logical home in the documentation of a single namespace or var.

Browsing good documentation should be a lot more like browsing the stacks in a library.
Search and cross-reference need to be strongly supported and aggressively used.
One should be able to wander into an area and quickly survey it.
Related material both more specific and more general should be easy to discover and access.

This project is an effort to explore writing a documentation tool somewhat in the style of sphinx, Eg.

- Offering good markup ergonomics with a convenient to learn syntax or set of small languages
- Enables the generation of API documents
- Enables the writing of other structured documents
- Enables the writing of structured examples
- Supports and encourages (due to convenient notation & link checking) the use of references between documents

## .repl files

Right now, this is just a sketch at a `.repl` file extension for writing examples.

A `.repl` file consists of a single `---` fenced EDN form much like a YAML header which may specify metadata about the examples in the file.
For instance, the `:prompt` keyword may be a string or pattern identifying the REPL prompt present in the examples.
Dependencies used in the REPL session may be specified with a Leiningen style `:dependencies` vector.
The namespace used may also be specified with the `:namespace` key.

[**example.repl**](example.repl):
```
---
{:namespace    user
 :dependencies [[org.clojure/clojure "1.8.0"]]
 :prompt       "user>"
}
---
user> (+ 1 1)
1
user> (conj #{:foo :bar} :baz)
#{:foo :bar :baz}
user>
```

Example files can be loaded into a data structure, describing the example as a whole and each given `(input, output)` pair.

For instance, in one version of stacks the above file loaded to the data structure

```clj
stacks.session> (parse-session (slurp "example.repl"))
{:tag :stacks.session/session,
 :profile {:prompt "user>",
           :namespace user,
           :dependencies [[org.clojure/clojure "1.8.0"]]},
 :pairs ({:tag :stacks.session/pair,
          :input "(+ 1 1)",
          :results "1"}
         {:tag :stacks.session/pair,
          :input "(conj #{:foo :bar} :baz)",
          :results "#{:foo :bar :baz}"})}
```

From this structure and given the namespace in which the session occurs it would be possible to use `clojure.tools.analyzer.jvm` to perform expression analysis & macroexpansion for full syntax highlighting.
There are some challenges there because of how tools.analyzer treats inlinable expressions, but using tools.analyzer would allow for much fuller syntax highlighting and the automatic cross-linking of forms with their documentation.

## Notation

There are unfortunately many variations of Markdown.
However, this also means that it's widely available and relatively widely understood.
For all its flaws, it's the most convenient baseline notation to work from.
There simply isn't a broad enough understanding of or support for other notations such as reStructuredText outside of their particular niches.

[var-link](https://github.com/clojure-grimoire/var-link) isn't the notation you want, it's too verbose but it's the right concept.
I propose that the URI patterns `def:name`, `def:namespace/name`, `namespace:name`, `topic:name`, `class:classname`, `javadoc:classname`, `javadoc:classname/methodname` and such are sufficiently terse to be author friendly, unobtrusive in source code and trivial to write transformers for when performing internal linking of documentation.
Furthermore, the URI syntax is already legal and expected to be well-behaved nested within Markdown as the target of links and images.

## License

Copyright © 2017 Reid "arrdem" McKenzie

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
