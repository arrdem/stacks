# Projects

Ergonomics matter.
If a tool requires overly much effort, nobody will use it.
Even if compelled to use it, they'll do so as little as they can manage to preferring some ad-hoc solution which seems to better fit their needs.

If stacks is to be something which is _useful_, it must be something which is trivial/easy in the sense of requiring little learning or effort to adopt and still provide value.

[Leiningen](https://github.com/technomancy/leiningen) is the de-facto standard for building Clojure.
Consequently, stacks should have a story for operating as a Leiningen plugin, or at least a function of a project map for discovering source files and documents in order to do index or rich documentation generation.

### Demo: Project analysis

This is very much a work in progress, but some things work.

```clj+session {render=true}
---
{:namespace stacks.tools.projects
 :eval false}
---
;; Note, this example needs to be completely rewritten
;; To save you from looking at stacktraces, eval is disabled atm
stacks.doctree> (def *options (normalize-options default-options))
stacks.doctree> *options
stacks.doctree> (def *project
                  "A leiningen style project map with source paths,
                   also accepts doc paths and session paths."
                  {:source-paths ["src/" "test/"],
                   :doc-paths ["."]})
stacks.doctree> (def *files
                  (find-files *options *project))
stacks.doctree> *files
stacks.doctree> (index-sources *options *project *files)
```
