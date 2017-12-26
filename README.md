# Somewhere in the Stacks
<img align="right" src="https://github.com/arrdem/stacks/raw/master/etc/stacks.jpg" width=300/>

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

## Articles

REPL sessions are great, but aren't always the appropriate pedagogical tool, especially when much explaining is required.

Frequently it'd be better to interleave examples with fully formatted text.
This lets authors build their narrative while taking the greatest advantage of available formatting, while still being precise where precision is possible about the inputs outputs and results of evaluation.

Once documentation is in a rich data form, applying syntax highlighting and IDE-like code analysis becomes quite tractable.
Furthermore, the documentation itself becomes somewhat decoupled from its final rendering.

Something like this...

### Demo: Article parsing

[example.md](example.md)

    # Primordial Clojure {#primordial}

    ```clj/session {#ex1}
    ---
    {:namespace user
     :dependencies [[org.clojure/clojure "1.0.0"]]
     :session "primordial"
    }
    user> (def foo 3)
    #'user/foo
    ```

    Some continued text in the middle of the example, and now
    we want to continue the same session with another example

    ```clj/session {#ex2}
    ---
    {:session "primordial"}
    ---
    user> (+ foo 3)
    6
    ```

```clj
stacks.articles> (markdown->article (io/file "example.md"))
{:type :stacks.articles/article,
 :source #object[java.net.URL "0x5451e4aa" "file:/home/arrdem/doc/dat/git/arrdem/stacks/example.md"],
 :labels #{"primordial" "ex1" "ex2"},
 :references #{},
 :content ([:h1 {:id "primordial"} "Primordial Clojure"]
           {:type :stacks.articles/code,
            :tag "clj/session",
            :attrs {:id "ex1"}
            :content {:tag :stacks.sessions/session,
                      :profile {:prompt "^[^>]*?>\\s+",
                                :namespace user,
                                :dependencies [[org.clojure/clojure "1.9.0"]]},
                      :pairs ({:tag :stacks.sessions/pair,
                               :comment nil,
                               :input "(def foo 3)",
                               :results "#'user/foo"})}}
           [:p {}
            "Some continued text in the middle of the example,"
            " and now we want to continue the same session with another example"]
           {:type :stacks.articles/code,
            :tag "clj/session",
            :attrs {:id "ex2"}
            :content {:tag :stacks.sessions/session,
                      :profile {:prompt "^[^>]*?>\\s+",
                                :namespace user,
                                :dependencies [[org.clojure/clojure "1.9.0"]],
                                :session "primordial"},
                      :pairs ({:tag :stacks.sessions/pair,
                               :comment nil,
                               :input "(+ foo 3)",
                               :results "6"})}})}
stacks.articles>
```

## Projects

Ergonomics matter.
If a tool requires overly much effort, nobody will use it.
Even if compelled to use it, they'll do so as little as they can manage to preferring some ad-hoc solution which seems to better fit their needs.

If stacks is to be something which is _useful_, it must be something which is trivial/easy in the sense of requiring little learning or effort to adopt and still provide value.

[Leiningen](https://github.com/technomancy/leiningen) is the de-facto standard for building Clojure.
Consequently, stacks should have a story for operating as a Leiningen plugin, or at least a function of a project map for discovering source files and documents in order to do index or rich documentation generation.

### Demo: Project analysis

This is very much a work in progress, but some things work.

```clj
stacks.doctree> (def *options (normalize-options default-options))
#'stacks.doctree/*options
stacks.doctree> *options
{:doc-extensions #{#".*?\.md$" #".*?\.markdown$"},
 :source-extensions #{#".*?\.cljc$" #".*?\.clj$"},
 :session-extensions #{#".*?\.repl$"},
 :ignored-patterns #{#"checkouts"},
 :type :stacks.doctree/options}
stacks.doctree> (def *project
                  {:source-paths ["src/" "test/"], :doc-paths ["."]})
#'stacks.doctree/*project
stacks.doctree> (def *files
                  (find-files *options *project))
#'stacks.doctree/*files
stacks.doctree> *files
{:type :stacks.doctree/fileset,
 :sources (#object[java.net.URI
                   "0x63ab872"
                   "file:/home/arrdem/doc/dat/git/arrdem/stacks/src/stacks/articles.clj"]
           #object[java.net.URI
                   "0x7ea89027"
                   "file:/home/arrdem/doc/dat/git/arrdem/stacks/src/stacks/sessions.clj"]
           #object[java.net.URI
                   "0x521c3a20"
                   "file:/home/arrdem/doc/dat/git/arrdem/stacks/src/stacks/tools/namespace.clj"]
           #object[java.net.URI
                   "0x1b6f139"
                   "file:/home/arrdem/doc/dat/git/arrdem/stacks/src/stacks/tools/reader.clj"]
           #object[java.net.URI
                   "0x5fbff542"
                   "file:/home/arrdem/doc/dat/git/arrdem/stacks/src/stacks/doctree.clj"]
           #object[java.net.URI
                   "0x65ac7f3a"
                   "file:/home/arrdem/doc/dat/git/arrdem/stacks/test/stacks/sessions_test.clj"]),
 :docs (#object[java.net.URI
                "0x34bb38e"
                "file:/home/arrdem/doc/dat/git/arrdem/stacks/./.%23README.md"]
        #object[java.net.URI "0x56528495" "file:/home/arrdem/doc/dat/git/arrdem/stacks/./README.md"]
        #object[java.net.URI
                "0x605b885f"
                "file:/home/arrdem/doc/dat/git/arrdem/stacks/./example.md"]
        #object[java.net.URI
                "0x40ee74f3"
                "file:/home/arrdem/doc/dat/git/arrdem/stacks/./CHANGELOG.md"]),
 :sessions ()}
stacks.doctree> (index-sources *options *project *files)
({:type :stacks.doctree/file,
  :namespace
    {:tag :stacks.tools.namespace/namespace,
     :name stacks.articles,
     :metadata
       {:doc "A thin wrapper around common mark which adds extensions for writing stacks articles.",
        :authors ["Reid McKenzie <me@arrdem.com>"],
        :license "https://www.eclipse.org/legal/epl-v10.html"},
     :require
       {clojure.string
          {:type :stacks.tools.namespace/require,
           :namespace clojure.string,
           :as #{string},
           :rename {},
           :use? false},
        clojure.walk
          {:type :stacks.tools.namespace/require,
           :namespace clojure.walk,
           :as #{walk},
           :rename {},
           :use? false},
        clojure.edn
          {:type :stacks.tools.namespace/require,
           :namespace clojure.edn,
           :as #{edn},
           :rename {},
           :use? false},
        clojure.java.io
          {:type :stacks.tools.namespace/require,
           :namespace clojure.java.io,
           :as #{io},
           :rename {},
           :use? false},
        stacks.sessions
          {:type :stacks.tools.namespace/require,
           :namespace stacks.sessions,
           :as #{sessions},
           :rename {},
           :use? false},
        detritus.update
          {:type :stacks.tools.namespace/require,
           :namespace detritus.update,
           :as #{detritus.update},
           :rename {},
           :use? false,
           :refer [deep-merge]},
        commonmark-hiccup.core
          {:type :stacks.tools.namespace/require,
           :namespace commonmark-hiccup.core,
           :as #{mark},
           :rename {},
           :use? false}},
     :imports {FencedCodeBlock org.commonmark.node.FencedCodeBlock,
               Heading org.commonmark.node.Heading}},
  :url #object[java.net.URI "0x47ed0c5c" "file:/home/arrdem/doc/dat/git/arrdem/stacks/src/stacks/articles.clj"]},
 {:type :stacks.doctree/file, ...},
 ...)
```

## License

Copyright © 2017 Reid "arrdem" McKenzie

Distributed under the Eclipse Public License either version 1.0 or (at your option) any later version.
