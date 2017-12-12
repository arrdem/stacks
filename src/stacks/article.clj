(ns stacks.article
  "A thin wrapper around markdown-clj which adds extensions for writing stacks articles."
  {:authors ["Reid McKenzie <me@arrdem.com>"]
   :license "https://www.eclipse.org/legal/epl-v10.html"}
  (:require [clojure.string :as string]
            [markdown.transformers :as mt]
            [markdown.common :as mc]
            [markdown.core :refer [md-to-html-string*]]))

(defn read-all-string
  "Implementation detail of parse-options.

  Use Clojure's LispReader to read a sequence of forms from a string."
  [text]
  (loop [rdr     (clojure.lang.LineNumberingPushbackReader.
                  (java.io.StringReader. text))
         results []]
    (let [res (read rdr false :eof)]
      (if-not (= res :eof)
        (recur rdr (conj results res))
        results))))

(defn parse-options
  "Implementation detail.

  Used to parse sequences of key-value pairs where the key is a legal
  Clojure keyword and the value is either a raw string or a double
  quoted string supporting only escapes of the double quote
  character.

  Returns a map of keywords to string values."
  [text]
  (apply hash-map (read-all-string text)))

(defn make-codeblock-handler
  "Function of a code block transformer, used to build a markdown-clj handler for code blocks.

  Unfortunately, markdown-clj's transformers participate in the
  parsing process.  So there's not a good way to add a handler which
  just composes in nicely.  Consequently in order to get the desired
  code block parsing machinery, we have to do a full replacement of
  the default code block machinery.

  Parses the tail of a line following a triple backtick as a language
  name and key/value pairs.

  For instance:

  ```clj :profile foo :name ex1
  user> (+ 1 1)
  2
  ```

  Accepts a handler function (fn [state language options text]) -> [state, html],
  closes over that function, and calls it to render full buffers being
  whole blocks of code to HTML.

  Language key/value parameters are parsed via `#'parse-options`, and default to `{}`."

  ;; The strategy here is to accumulate all the text in a code block as :codeblock-buffer, and then
  ;; when we've got the whole thing call the handler function in a big bang that'll produce all the
  ;; relevant HTML. Because markdown-clj is designed to parse line by line and accumulate output
  ;; line by line, we need to cheat and emit a bunch of empty intermediary strings while we're
  ;; actually building up our buffer of text to syntax highlight or otherwise transform.
  [handler]
  (fn [text {:keys [codeblock codeblock-end codeblock-lang codeblock-options codeblock-buffer indented-code next-line]
             :as   state}]
    (let [trimmed           (string/trim text)
          next-line-closes? (= [\` \` \`] (take-last 3 (some-> next-line string/trim)))
          next-line-prefix  (when next-line-closes?
                              (apply str (first (string/split next-line #"```"))))]
      (cond
        codeblock-end
        (let [[state text] (handler state codeblock-lang codeblock-options codeblock-buffer)]
          [text
           (-> state
               (assoc :last-line-empty? true)
               (dissoc :code :codeblock :codeblock-end :codeblock-buffer :codeblock-lang :codeblock-options))])

        (and next-line-closes?
             codeblock)
        (let [buffer (str codeblock-buffer \newline
                          text \newline
                          next-line-prefix)]
          [""
           (assoc state
                  :skip-next-line?  true
                  :codeblock-end    true
                  :codeblock-buffer buffer
                  :last-line-empty? true)])

        (and (not indented-code)
             (= [\` \` \`] (take 3 trimmed)))
        (let [[lang code]    (split-with (partial not= \newline) (drop 3 trimmed))
              [lang options] (split-with (partial not= \space) lang)
              lang           (if lang (apply str lang) nil)
              options        (parse-options (apply str options))
              s              (apply str (rest code))]
          [""
           ;; Note: This is gigantic but it's me being cleaver. The original implementation in
           ;; markdown-clj used an (if next-line-closes?) form, which when stared at revealed itself
           ;; to actually not matter. Writing the state update in this form actually simplified the
           ;; computation of the next buffer state as well.
           (assoc state
                  :code              true
                  :codeblock         true
                  :codeblock-lang    lang
                  :codeblock-options options
                  :codeblock-buffer  (cond (and (not-empty s) next-line-closes?)
                                           (str s \newline next-line-prefix)

                                           (and (not-empty s) (not next-line-closes?))
                                           s

                                           (and (empty? s) next-line-closes?)
                                           next-line-prefix

                                           :default
                                           "")
                  :codeblock-end     next-line-closes?
                  :skip-next-line?   next-line-closes?)])

        codeblock
        ["" (update state :codeblock-buffer str \newline text)]

        :default
        [text state]))))

(defn make-transformer-vector
  "Compute a markdown-clj transformers vector which will handle code-blocks more generally.

  Accepts a transformer function (fn [state language options text]) -> [state, html],
  returning a new vector of markdown-clj transformers. The returned
  transformers should be used as `:replacement-transformers` when
  calling into markdown-clj."
  [code-transformer]
  ;; Transform the default vector of transformers by replacing the stock code-block handler with the
  ;; computed new one.
  (mapv (fn [e]
          (if (= e mt/codeblock)
            (make-codeblock-handler code-transformer)
            e))
        mt/transformer-vector))

(defmulti highlight-code
  "Highlights the given text as code in the given language, returning raw HTML."
  (fn [_state language _options _text] language))

(defmethod highlight-code :default [state _language _options text]
  [state (str "<pre><code>" (mc/escape-code text) "</code></pre>")])

(defn md-to-html-string
  "Wrapper around `#'markdown.core/md-to-html-string` which uses `#'highlight-code` to render code."
  [text & params]
  (:html (md-to-html-string* text (list* :replacement-transformers (make-transformer-vector #'highlight-code) params))))
