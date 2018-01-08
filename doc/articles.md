# Articles

REPL [Sessions](sessions.md) are great, but aren't always the appropriate pedagogical tool, especially when much explaining is required.

Frequently it'd be better to interleave examples with fully formatted text.
This lets authors build their narrative while taking the greatest advantage of available formatting, while still being precise where precision is possible about the inputs outputs and results of evaluation.

Once documentation is in a rich data form, applying syntax highlighting and IDE-like code analysis becomes quite tractable.
Furthermore, the documentation itself becomes somewhat decoupled from its final rendering.

### Demo: Article parsing

[**example.md**](/src/test/resources/example.md)

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

