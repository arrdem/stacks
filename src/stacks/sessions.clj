(ns stacks.sessions
  "Tools for parsing REPL style sessions into data structures."
  {:authors ["Reid McKenzie <me@arrdem.com>"]
   :license "https://www.eclipse.org/legal/epl-v10.html"}
  (:require [clojure.java.io :as io]
            [clojure.tools.reader.reader-types :refer [read-char]]
            [rewrite-clj.parser :as p]
            [rewrite-clj.reader :refer [string-reader]])
  (:import java.io.Reader))

(def header-regex
  "Pattern used to match a YAML-style document header"
  #"(^---\n((.*\n)*?)---\n)?((.*\n)+)")

(def default-prompt-pattern
  "Default pattern for recognizing prompts.
  Assumes that > is the prompt character, and that it may be followed by a bunch of whitespace."
  "^[^>]*?>\\s+")

(def default-profile
  "Default pseudo-leiningen profile in which to analyze an example.ss"
  {:prompt       default-prompt-pattern
   :namespace    'user
   :dependencies '[[org.clojure/clojure "1.9.0"]]})

(defn make-pair-pattern
  "Constructs a pattern which matches the given prompt (or prompt
  pattern), followed by arbitrarily much output until either the end
  of input, or the next occurrence of the prompt."
  [prompt-or-pattern]
  (let [p (str prompt-or-pattern)]
    ;; This pattern is a bit involved.
    ;; - Set dotall with (?s) so that . includes newlines.
    ;; - Match any preceding ";" comment lines
    ;; - Match the prompt pattern, followed by whitespace.
    ;; - Match a bunch of text lazily, being the input form & results.
    ;; - Use lookahead to another comment, prompt or the end of file to anchor the end of the results.
    (-> (format "(?s)([\\s&&[^\n\r]]*;[^\n]*?\n)*?(%s\\s*)(.*?)((?=(%s)|([\\s&&[^\n\r]]*;[^\n]*?\n))|\\Z)" p p)
        (re-pattern))))

(defn slurp-reader
  "Consumes all available input from a tools.reader reader, returning a string thereof."
  [rdr]
  (loop [buff (java.lang.StringBuilder.)]
    (let [c (read-char rdr)]
      (if c
        (do (.append buff c)
            (recur buff))
        (.trim (.toString buff))))))

(defn parse-pairs
  "Parses the sequence of input/output pairs from the session's body.

  Returns a sequence of `::pair` structures, representing an input
  form and its results as unstructured text."
  [{:keys [prompt] :or {prompt default-prompt-pattern}} buffer]
  (for [match (re-seq (make-pair-pattern prompt) buffer)
        :let  [;; Destructure the match
               [_match comment? _prompt text _prompt?] match
               ;; Make a mutable reader over the matched text.
               ;;
               ;; At this point the text contains both the input form, and the printed results of
               ;; evaluation.
               reader (string-reader text)
               ;; Use rewrite-clj too parse the first form. This is the input form.
               ;;
               ;; Unfortunately there may be syntax errors. Deal with this by producing a pair
               ;; `[rdr, form?]` so that we can "reset" the reader when there are syntax errors.

               ;; FIXME (arrdem 2017-12-10): In the case of a syntax error, the performance here is
               ;;   pretty awful because we round-trip the string through a reader to another string
               ;;   for no reason.
               [reader form] (try [reader (p/parse reader)]
                                  (catch Exception e
                                    [(string-reader text) nil]))
               ;; Consume the rest of the text, it's the results of evaluation.
               text (slurp-reader reader)]
        ;; There may not have been output, or input. For instance if the match was just a prompt or
        ;; something else.
        :when (and form text)]
    {:tag     ::pair
     :comment comment?
     :input   (str form)
     :results text}))

(defn parse-session
  "Parses a session file, returning a datastructure representing the session.

  Session files are comprised of an optional `---` wrapped EDN header,
  and a sequence of prompts, expressions and results.

  The EDN header structure may specify arbitrary key/value pairs,
  however some are treated specially.

  - The namespace of the session as a symbol in the `:ns` key
  - Dependencies in the Leiningen style in the `:dependencies` key
  - A custom pattern string to be used to recognize the session's prompt with the `:prompt` key
  - A unique identifier for the session with the `:id` key

  If no `:dependencies` are listed, Clojure 1.9 (or newer) will be used.
  If no `:prompt` pattern is listed, the prompt is assumed to be the > character.
  If no `:namespace` is listed, the `'user` namespace will be assumed.
  If no `:id` is provided, an ID will be generated from a hash of the session."
  ([text]
   (parse-session default-profile text))
  ([profile text]
   (let [[_ _ kvs _ body :as match] (re-find header-regex text)
         example-profile            (if kvs (read-string kvs) {})
         ;; FIXME (arrdem 2017-12-10):
         ;;   merge profiles in some remotely sane way.
         profile                    (merge profile example-profile)]
     {:tag     ::session
      :profile profile
      :pairs   (parse-pairs example-profile body)})))
