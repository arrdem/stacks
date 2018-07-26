# Projects

Ergonomics matter.
If a tool requires overly much effort, nobody will use it.
Even if compelled to use it, they'll do so as little as they can manage to preferring some ad-hoc solution which seems to better fit their needs.

If stacks is to be something which is _useful_, it must be something which is trivial/easy in the sense of requiring little learning or effort to adopt and still provide value.

The Clojure ecosystem already has several tools - lein, boot and deps.edn to name a few - which help users automate packaging source files together.
Unfortunately, packaged source files don't tell the whole story.
There is repository history, configuration data, and documentation, all of which should tie together into the artifact as understood by someone approaching it cold.

Stacks projects are intended to map fairly directly to projects as developers understand them;
particularly as they are understood by Leiningen and to a slightly lesser extent deps.edn.

A project is described using a single level mapping -
```clj+session
---
{:namespace stacks.tools.projects
 :eval true
 :session "project-demo"}
---
> (def *project
    {:source-paths ["src/main/clj"
                    "src/main/cljc"
                    "src/dev/clj"
                    "src/dev/cljc"]
     :test-paths ["src/test/clj"
                  "src/test/cljc"]
     :doc-paths ["doc"
                 "README.md"]})
```

This structure provides a number of directory paths which will be considered as source roots, more paths considered to be test roots, and some paths where documentation may be found.
Coincidentally, this is exactly the configuration which the Stacks instance you're reading now runs with -

```clj+session
---
{:session "project-demo"}
---
> (= *project stacks.server/+project+)
```

Once a project descriptor exists as data, we can use it to drive exploring the filesystem.
This is exactly how the sidebar on the left is built.
Stacks uses the project descriptor to find all files in the project (according to some settings), and builds an index from them called a doctree.

### Demo: Doctree analysis

```clj+session
---
{:session "project-demo"}
---
;; These are the default options suggested for building doctrees
> +default-options+
;; And now to build a doctree
> (def *doctree
   (project->doctree
     +default-options+
     *project))
> (keys *doctree)
;; Even for small projects, the doctree is /huge/
> (count (pr-str *doctree))
```

The doctree structure currently contains three kinds of records - `sources`, `docs` and `sessions`.
More document kinds may be added in the future.

```clj+session
---
{:session "project-demo"}
---
> (count (:sources *doctree))
> (count (:docs *doctree))
> (count (:sessions *doctree))
```

Sources represent, well, source files.
Specifically, as visible in the `+default-options+` above, Clojure and `cljc` files are recognized as sources.

Docs represent general documents, by default recognized with `.md` and `.markdown` extensions.
Documents are presumably [Articles](/doc/articles.md), and are processed as such.

Sessions, are of course [Sessions](/doc/sessions.md).

It's important to note that doctrees are purely data.
Although users may customize the machinery by which doctrees and their component structures are built (say adding Article parser middleware), doctrees are data structures which can be processed and exist before any content rendering occurs.
This is, to my taste, Stacks' primary strength.

Grimoire always suffered because there was only ever a weak backing data representation for per-symbol documentation, and articles never had a meaningful data representation.
This made for instance cross-referencing impossible.
How do you find what documents link to which others?
I didn't - all the "see also" annotations on the site were hand-edited.

Creating a data presentation of the site makes it easy to perform closure based links-to and linked-to-by operations.
It makes finding dead links possible.

It makes dynamic linking schemes possible.
What if I suddenly wanted to provide support for resolving vars?
`#'foo/bar` could easily be resolved as an absolute link from an Article body, and `#'foo` could easily be resolved within the namespace context say of a docstring.
With a data representation of the content to be rendered, finding `[:code "#'foo"]` nodes and rewriting them to `[:a {:href "/var/some-ns/foo"} [:code "#'foo"]]` for instance would be trivial.

Similarly supporting other content types like Clojure specs becomes possible.
It's just a middleware which adds more links and link anchors to the doctree.
