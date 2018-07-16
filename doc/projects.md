# Projects

Ergonomics matter.
If a tool requires overly much effort, nobody will use it.
Even if compelled to use it, they'll do so as little as they can manage to preferring some ad-hoc solution which seems to better fit their needs.

If stacks is to be something which is _useful_, it must be something which is trivial/easy in the sense of requiring little learning or effort to adopt and still provide value.

[Leiningen](https://github.com/technomancy/leiningen) is the de-facto standard for building Clojure.
Consequently, stacks should have a story for operating as a Leiningen plugin, or at least a function of a project map for discovering source files and documents in order to do index or rich documentation generation.

### Demo: Project analysis

This is very much a work in progress, but some things work.

```clj+session
---
{:namespace stacks.tools.projects
 :eval true}
---
> (set! *print-length* 3)
> (project->doctree
   +default-options+
   {:source-paths ["src/main/clj"
                   "src/main/cljc"
                   "src/dev/clj"
                   "src/dev/cljc"]
    :test-paths ["src/test/clj"
                 "src/test/cljc"]
    :doc-paths ["doc"]})
```

The idea here is that a whole project can be slurped, indexed and compiled into something I'm calling a doctree.
The doctree of a project encompasses all the content, and critically all the addressable "entities".
This allows Stacks at least in theory to be used as a static site generator.
By emitting a static mapping of files (and addressable entities) to URLs,
