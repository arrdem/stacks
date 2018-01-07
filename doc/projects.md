# Projects

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
                  "A leiningen style project map with source paths,
                   also accepts doc paths and session paths."
                  {:source-paths ["src/" "test/"],
                   :doc-paths ["."]})
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
