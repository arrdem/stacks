# Somewhere in the Stacks
<img align="right" src="https://github.com/arrdem/stacks/raw/master/etc/stacks.jpg" width=300/>

**TL;DR** A sketch at a longer-form documentation tool for Clojure.

## Manifesto

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

## Table of Contents

- [**Articles**](/doc/articles.md), CommonMark formatted documents as data, with extensible code blocks.
- [**Sessions**](/doc/sessions.md), REPL sessions as data.
- [**Doctests**](/doc/doctests.md), REPL examples as test cases.
- [**Projects**](/doc/projects.md), Project structures as data.

## Usage

Stacks isn't yet ready for use, but features a prototype server - [stacks.server](/src/main/clj/stacks/server.clj) - which supports articles and sessions as they are currently implemented.
It's designed to serve Stacks' own documentation, and used to develop the system.
With some legwork it could probably be adapted to fit your needs, but I haven't packaged it more generally yet.

## License

Copyright © 2017 Reid "arrdem" McKenzie

Distributed under the Eclipse Public License either version 1.0 or (at your option) any later version.
