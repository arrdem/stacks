# Primordial Clojure {#primordial}

```clj+session {#ex1}
---
{:namespace user
 :dependencies [[org.clojure/clojure "1.0.0"]]
 :session "primordial"}
---
user> (def foo 3)
#'user/foo
```

Some continued text in the middle of the example, and now
we want to continue the same session with another example

```clj+session {#ex2}
---
{:session "primordial"}
---
user> (+ foo 3)
6
```
