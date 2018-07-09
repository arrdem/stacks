(ns stacks.tools.doctests
  "Tools for parsing doctests as proposed by Norbert Wójtowicz into data structures."
  {:authors ["Reid \"arrdem\" McKenzie <me@arrdem.com>"
             "Norbert \"pithyless\" Wójtowicz <wojtowicz.norbert@gmail.com>"]}
  (:refer-clojure :exclude [compile])
  (:require [stacks.tools.sessions :as sessions]))

(defn normalize-profile
  "Ensure that a profile "
  [{:keys [eval-prompt is-prompt valid-prompt] :as profile}]
  {:pre [(string? eval-prompt)
         (string? is-prompt)
         (string? valid-prompt)]}
  (assoc profile
         :prompt (format "%s|%s|%s" eval-prompt is-prompt valid-prompt)))

(def default-profile
  "The default profile used when parsing doctests."
  (-> (assoc sessions/default-profile
             :eval-prompt ">>"
             :is-prompt "=>"
             :valid-prompt ":>"
             :as "%")
      normalize-profile))

(defn ->test [{:keys [is-prompt] :as profile} {:keys [prompt comment input] :as pair}]
  {:type    (if (= is-prompt prompt)
              ::is ::valid)
   :input   input
   :comment comment})

(defn group-pairs-into-tests [{:keys [eval-prompt] :as profile} pairs]
  (let [counter         (volatile! 0)
        example-counter (fn [prompt]
                          (if-not (= prompt eval-prompt)
                            @counter
                            (vswap! counter inc)))]
    (->> pairs
         (partition-by #(example-counter (:prompt %)))
         (map (fn [[eval & tests]]
                (merge (dissoc eval :type :prompt)
                       {:type       ::doctest
                        :assertions (map (partial ->test profile) tests)}))))))

(defn parse-doctests
  "Parses the given text as a doctest document, with optional arguments.

  Doctests accept all the same profile options as sessions, as well as
  some new ones.

  Doctests have not one prompt option but three:
   - `:eval-prompt`, precedes an evaluation form (default `>>`)
   - `:is-prompt`, precedes an `#'clojure.test/is` assertion (default `=>`)
   - `:valid-prompt`, precedes a `#'clojure.spec.alpha/valid?` assertion (default `:>`)

  Doctests also accept a string (symbol) (default `%`) to bind the
  value of the last eval form to. This allows is and conforms
  assertions to reference the previous value explicitly.

  ```clj+doctest
  ---
  {:namespace stacks.tools.doctests}
  ---
  >> (parse-doctests \">> (+ 1 1)\n=> (= % 2)\n\")
  :> ::doctests
  => (= (first (:tests %))
        {:type ::doctest
         :as \"%\"
         :assertions ({:type ::is
                       :input \"(= % 2)\"})})
  ```
  "
  ([text]
   (parse-doctests default-profile text))
  ([profile text]
   (let [[_ _ kvs _ body :as match] (re-find sessions/header-regex text)
         example-profile            (if kvs (read-string kvs) {})
         ;; FIXME (arrdem 2018-01-04):
         ;;   merge profiles in some remotely sane way.
         profile                    (-> (merge profile example-profile)
                                        normalize-profile)]
     {:type    ::doctests
      :profile profile
      :tests   (->> (sessions/parse-pairs profile body)
                    (group-pairs-into-tests profile))})))

(defn emit-assertion [{:keys [as] :as profile} {:keys [type input] :as assertion}]
  (cond (= ::valid type)
        `(clojure.test/is (clojure.spec.alpha/valid? ~(read-string input) ~(symbol as)))
        (= ::is type)
        `(clojure.test/is ~(read-string input))))

(defn doctest->block [{:keys [as namespace] :as profile} {:keys [input assertions]}]
  `(let [~(symbol as) ~(read-string input)]
     ~@(mapv (partial emit-assertion profile) assertions)))

(defn compile-doctests [{:keys [profile tests] :as doctests}]
  (binding [*ns* (clojure.lang.Namespace/findOrCreate (symbol (:namespace profile)))]
    (eval `(fn []
             ~@(mapv (partial doctest->block profile) tests)))))
