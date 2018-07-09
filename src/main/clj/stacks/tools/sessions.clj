(ns stacks.tools.sessions
  "Tools for parsing REPL style sessions into data structures."
  {:authors ["Reid McKenzie <me@arrdem.com>"]
   :license "https://www.eclipse.org/legal/epl-v10.html"}
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [stacks.tools.prepl :as prepl :refer [prepl resolve-fn]]
            [stacks.tools.reader :refer [read-source read-whitespace]]
            [stacks.tools.pygments :refer [pygmentize]])
  (:import [java.io
            ,,PushbackReader
            ,,Reader
            ,,StringReader]))

(def header-regex
  "Pattern used to match a YAML-style document header"
  #"(^---\n((.*\n)*?)---\n)?((.*\n)+)")

(def default-prompt-pattern
  "Default pattern for recognizing prompts.
  Assumes that > is the prompt character, and that it may be followed by a bunch of whitespace."
  "^[\\w\\.&&[^>]]{0,16}?>")

(def default-profile
  "Default pseudo-leiningen profile in which to analyze an example."
  {;; The pattern string used for parsing
   :prompt default-prompt-pattern
   ;; The namespace in which evaluation should occur
   :namespace 'user
   ;; Leiningen style dependencies (ignored for now, gets Clojure "for free")
   :dependencies '[[org.clojure/clojure "1.9.0"]]
   ;; Whether or not the session needs to be evaluated to be rendered
   ;; - eg whether all input and output is already included in the
   ;; documented and formatted as the user wishes it to be.
   ;;
   ;; This is off by default as a conservative setting - stacks seeks
   ;; to avoid being really smart unless the user desires it, and
   ;; performing code evaluation is at least smart if not magical.
   :eval false
   ;; A symbol resolving to the function to be used for formatting the
   ;; results of evaluation. Printing occurs in the same context as
   ;; evaluation - this allows for the use of custom or 3rdparty
   ;; printing behavior.
   ;;
   ;; Default's to Clojure's default printer.
   :printer 'clojure.pprint/pprint})

(defn make-pair-pattern
  "Constructs a pattern which matches the given prompt (or prompt
  pattern), followed by arbitrarily much output until either the end
  of input, or the next occurrence of the prompt."
  [prompt-or-pattern]
  (let [p (str prompt-or-pattern)]
    ;; This pattern is a bit involved.
    ;; - Set dotall with (?s) so that . includes newlines.
    ;; - Match any preceding ";" comment lines
    ;; - Match the prompt pattern
    ;; - Match (but do not capture!) whitespace greedily
    ;; - Match a bunch of text lazily, being the input form & results.
    ;; - Use lookahead to another comment, prompt or the end of file to anchor the end of the results.
    (-> (format "(?sm)([\\s&&[^\n\r]]*;[^\n]*?\n)*?(^%s)(?:\\s*+)(.*?)((?=(^%s)|([\\s&&[^\n\r]]*;[^\n]*?\n))|\\Z)" p p)
        (re-pattern))))

(defn parse-pair-match
  "Parse a single pair pattern match, returning a `::pair` structure."
  [match]
  (let [;; Destructure the match
        [_match comment? prompt text _prompt?] match
        ;; Make a mutable reader over the matched text.
        ;;
        ;; At this point the text contains both the input form, and the printed results of
        ;; evaluation.
        reader (StringReader. text)
        ;; Use rewrite-clj too parse the first form. This is the input form.
        ;;
        ;; Unfortunately there may be syntax errors. Deal with this by producing a pair
        ;; `[rdr, form?]` so that we can "reset" the reader when there are syntax errors.

        ;; FIXME (arrdem 2017-12-10): In the case of a syntax error, the performance here is
        ;;   pretty awful because we round-trip the string through a reader to another string
        ;;   for no reason.
        [reader form] (try [reader (read-source reader)]
                           (catch Exception e
                             ;; Return a new Reader and pretend we didn't see anything.
                             ;;
                             ;; FIXME (arrdem 2017-12-29):
                             ;;   Is there a good way to enable users to capture this event?
                             [(StringReader. text) nil]))

        ;; Eat any whitespace between the end of the prompt form and the result
        reader (read-whitespace (PushbackReader. reader))

        ;; Consume the rest of the text, it's the results of evaluation.
        text          (slurp reader)]
    {:type    ::pair
     :prompt  prompt
     :comment comment?
     :input   (str form)
     :results [{::val text}]}))

(defn parse-pairs
  "Parses the sequence of input/output pairs from the session's body.

  Returns a sequence of `::pair` structures, representing an input
  form and its results as unstructured text."
  [{:keys [prompt]
    :or {prompt default-prompt-pattern}}
   buffer]
  (map parse-pair-match (re-seq (make-pair-pattern prompt) buffer)))

(defonce +session-registry+
  (atom {}))

(defn parse-session
  "Parses a session, returning a datastructure representing the session.

  Sessions are comprised of an optional `---` wrapped EDN header,
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
         profile                    (merge profile example-profile)
         ;; Intern (or load) the session's profile so others can refer to it
         profile (if-let [session (:session profile)]
                   (locking +session-registry+
                     (if (contains? @+session-registry+ session)
                       ;; default - load
                       (get @+session-registry+ session)
                       ;; store
                       (do (swap! +session-registry+
                                  assoc session profile)
                           profile)))
                   profile)]

     {:type    ::session
      :profile profile
      :pairs   (parse-pairs profile body)})))

(defn evaluate-session
  "Given a parsed session structure, evaluate it (if evaluation was
  requested), returning an updated session."
  ([session]
   (evaluate-session (:profile session default-profile) session))
  ([{:keys [dependencies namespace printer] :as profile}
    session]
   ;; FIXME (arrdem 2018-07-08):
   ;;
   ;;   This is the shittiest possible implementation of taking a
   ;;   session structure and evaluating it to produce a "rendered"
   ;;   Hiccup sub-document.
   ;;
   ;;   Ideally code execution would occur in a contained, parallel
   ;;   environment such as a boot pod or even a separate JVM, but
   ;;   here we are.
   (update session
           :pairs
           (fn [pairs]
             (if-not (or (:evaluate profile)
                         (:eval profile)
                         (:evaluate session)
                         (:eval session))
               ;; Users can opt out, it's off by default
               pairs

               ;; If the user wants us to eval the session...
               (let [acc (atom [])]
                 (prepl (clojure.lang.LineNumberingPushbackReader.
                         (StringReader.
                          (str/join "\n" (map :input pairs))))
                        (fn [{:keys [type val] :as res}]
                          (swap! acc conj
                                 (if (= type ::prepl/ret)
                                   (assoc res ::val
                                          (binding [*out* (java.io.StringWriter.)]
                                            ((resolve-fn printer) val)
                                            (.toString *out*)))
                                   res)))
                        :ns namespace)
                 (map-indexed (fn [idx pair]
                                (let [results (filter #(= (:form-id %) idx) @acc)
                                      ret (first (filter #(= (:type %) ::prepl/ret) results))]
                                  (assoc pair
                                         :results (vec results)
                                         :namespace (:ns ret))))
                              pairs)))))))

(defn render-session
  "Given a parsed (and evaluated) session structure, render it to Hiccup."
  [{:keys [pairs] :as session}]
  [:div {:class "session highlight"}
   (for [{:keys [comment input results namespace]} pairs
         :when (not (empty? input))] ;; Empty inputs missbehave :/
     [:div.pair
      (when comment
        [:pre.comment comment])
      [:pre.readline
       [:span.namespace.nf namespace]
       [:span.prompt "=>"]
       [:span.input (str/trim (pygmentize "clj" input))]]
      [:div.results
       (for [{:keys [type stream]
              bare-val :val
              formatted-val ::val} results]
         [:pre {:class (cond (= type ::prepl/stream) (name stream)
                             (= type ::prepl/ret) "ret")}
          (let [val (or formatted-val bare-val)]
            (str/trim
             (if (= type ::prepl/ret)
               (pygmentize "clj" val)
               val)))])]])])
