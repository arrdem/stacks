(ns stacks.tools.javadoc
  "Tools for locating and extracting Java documentation from Javadocs.

  This namespace is HEAVILY inspired by `clojure.java.javadoc`, which
  has a particular and dated API unsuited to composition or
  programmatic access. However it does not strive to be a drop-in
  replacement."
  {:authors ["Reid \"arrdem\" McKenzie <me@arrdem.com>"
             "Christophe Grand"
             "Stuart Sierra"]
   :license "https://www.eclipse.org/legal/epl-v10.html"}
  (:require [clojure.string :as str]
            [clojure.java.browse :refer [browse-url]]
            [clojure.java.io :as io]
            [clojure.zip :as z]
            [detritus.update :refer [map-vals]]
            [net.cgrand.enlive-html :as html])
  (:import [java.net URL URLConnection]
           [java.io File FileInputStream InputStream]
           [me.arrdem PeekPushBackIterator]))

(defn get-classpath []
  (str/split (System/getProperty "java.class.path") #":"))

(defn java-version->javadoc-url
  "Maps a JDK version eg. \"1.2\", \"1.9\" etc. to a the Java SE documentation on docs.oracle.com.

  Assumes that Oracle can't and won't change the docs location because compatibility rues all.
  Assumes that we'll never see a Java 2.0, and so only the minor version matters."
  [java-version]
  (let [[major minor] (str/split java-version #"\.")]
    (format "http://docs.oracle.com/javase/%s/docs/api/" minor)))

(defn java-version->jdk-roots
  "Maps a JDK version to an options map providing remote roots for the JDK distributed classes."
  [jdk-version]
  (let [jdk-url (java-version->javadoc-url jdk-version)]
    {:type         ::options
     :remote-roots {"java."          jdk-url
                    "com.oracle."    jdk-url
                    "com.sun."       jdk-url
                    "sun."           jdk-url
                    "javax."         jdk-url
                    "org.ietf.jgss." jdk-url
                    "org.omg."       jdk-url
                    "org.w3c.dom."   jdk-url
                    "org.xml.sax."   jdk-url}}))

(def empty-options
  {:type         ::options
   :remote-roots {}
   :local-roots  {}})

(def merge-options
  "Function for merging options maps together."
  (partial merge-with
           #(if (and (map? %1) (map? %2))
              (merge %1 %2) %2)))

(def default-options
  "The default options map used for locating Javadocs.

  `:javadoc-roots` must be a map from package prefix (ending in \".\") to a URI or URL."
  (let [jdk-version (System/getProperty "java.specification.version")]
    (merge-options empty-options
                   (java-version->jdk-roots jdk-version))))

(defn as-url [str-or-url]
  (if (string? str-or-url)
    (URL. str-or-url)
    (if (instance? URL str-or-url)
      str-or-url)))

(defn indicies-of [^String text ^String s]
  (loop [acc  []
         last (.indexOf text s)]
    (if-not (= -1 last)
      (recur (conj acc last) (.indexOf text s (inc last)))
      acc)))

(defn prefixes-by-including
  "Returns all prefixes of the `text` which are delimited by an
  occurrence of the provided string `s`.

  Includes the delimiter in all prefixes."
  [^String text ^String s]
  (for [idx (reverse (indicies-of text s))]
    (.substring text 0 (+ idx (count s)))))

(defn javadoc-url-for-package*
  "Implementation detail.

  Searches a roots map for a URL for the given class name."
  [roots package-name]
  {:pre [(every? #(.endsWith ^String % ".") (keys roots))]}
  (loop [[pfx & prefixes] (prefixes-by-including package-name ".")]
    (or (some-> (get roots pfx) as-url)
        (if prefixes
          (recur prefixes)))))

(defn javadoc-url-for-package
  "Try to locate a root URL for Javadocs including the given package.

  Searches first the configured `:local-roots` mapping and then the
  `:remote-roots` for a mapping of a prefix of the given
  `package-name` to a URL.

  `package-name` and all package prefix in both `:local-roots` and
  `:remote-roots` must end with \".\""
  [{:keys [local-roots remote-roots] :as options} package-name]
  {:pre [(.endsWith package-name ".")]}
  (or (javadoc-url-for-package* local-roots package-name)
      (javadoc-url-for-package* remote-roots package-name)))

(defn javadoc-url-for-class
  "Returns a full Javadoc URL for a given fully qualified `class-name`
  Eg. \"com.foo.Bar\" and a URL corresponding to a Javadoc root
  expected to contain documentation for the given `class-name`.

  `class-name` must be a valid class which is resolvable in the
  current classloader context."
  [package-url ^String class-name]
  {:pre [(Class/forName class-name)]}
  (URL. package-url (str (.replace class-name \. \/) ".html")))

(def ^:dynamic *user-options*
  "Reference containing options to be used by `#'javadoc` when none are provided.

  The referenced options should be an options map per `#'default-options`.

  Programmatic access should pass options explicitly rather than rely
  on this behavior."
  (atom default-options))

(defn browse-javadoc-for
  "Attempts to open a browser window viewing the Javadocs for the given object or class.

  If no options are provided, uses `#'*user-options*` which defaults
  to `#'default-options` unless modified or bound by the user.

  Programmatic access should pass options explicitly rather than rely
  on this behavior."
  ([class-or-object]
   (browse-javadoc-for @*user-options* class-or-object))
  ([options class-or-object]
   (let [^Class c             (if (instance? Class class-or-object)
                                class-or-object
                                (class class-or-object))
         ^String package-name (str (.getName (.getPackage c)) ".")]
     (if-let [package-url (javadoc-url-for-package options package-name)]
       (let [javadoc-url (javadoc-url-for-class package-url (.getName c))]
         (browse-url javadoc-url)
         javadoc-url)
       {:type    ::error
        :message "Could not find Javadoc for package"
        :package package-name
        :class   (.getName c)
        :options options}))))

(defn ^URLConnection set-properties [^URLConnection req properties]
  (doseq [[k v] properties]
    (.setRequestProperty req "User-Agent" "Mozilla/5.0"))
  req)

(defn fetch-url*
  [properties url]
  (let [connection (-> (as-url url)
                       .openConnection
                       (set-properties properties)
                       (doto (.setUseCaches true)))]
    {:type    ::connection
     :code    (.getResponseCode connection)
     :headers (into {} (.getHeaderFields connection))
     :stream  (.getInputStream connection)}))

(defn fetch-url
  "Implementation detail.

  Attempt to fetch a response from a URL as a simple `InputStream`,
  following redirects with a limit."
  ([url]
   (fetch-url {:redirect-limit 10
               :redirect-count 0
               :original-url   url}
              ;; FIXME (arrdem 2017-12-29):
              ;;   any other voodoo need to go here?
              {"User-Agent" "Mozilla/5.0"}
              url))
  ([{:keys [redirect-limit redirect-count] :as state} properties url]
   (if-not (>= redirect-count redirect-limit)
     (let [{:keys [stream code headers] :as resp} (fetch-url* properties url)]
       (if (<= 300 code 399)
         (if-let [url* (first (get headers "Location"))]
           (recur (update state :redirect-count inc) properties url*))
         (if (<= 200 code 299)
           stream
           {:type    ::error
            :headers headers
            :code    code
            :msg     (slurp stream)})))
     {:type ::error
      :msg  "Exhausted the redirect limit!"
      :url  (:original-url state)})))

(defn without-ref [url]
  (str/replace (.toString url) #"#[^?]*" ""))

(defn classref [base-url {:keys [attrs content] :as a}]
  (when (-> attrs :href)
    {:type       :java/type
     :name       (first content)
     :primitive? false
     :url        (URL. base-url (:href attrs))}))

(defn methodref [base-url {:keys [attrs content] :as a}]
  (when (-> attrs :href)
    {:type  :java/method
     :class (classref base-url (update-in a [:attrs :href] without-ref))
     :name  (first content)
     :url   (URL. base-url (:href attrs))}))

(defn name= [x]
  (html/pred #(= (-> % :attrs :name) x)))

(defn has-child [x]
  (html/pred #(if-let [children (z/children %)]
                (some x children))))

(defn parse-javadoc-inheritance [url html]
  (let [supers (map (partial classref url)
                    (html/select html [:ul.inheritance :li :a]))
        name   (first (:content (last (html/select html [:ul.inheritance :li]))))]
    {:supers supers
     :name   name}))

(def summary-delimeters
  {" =========== FIELD SUMMARY =========== " :fields
   " ======== CONSTRUCTOR SUMMARY ======== " :constructors
   " ========== METHOD SUMMARY =========== " :methods})

(defn partition-by-comments [delimeters content]
  (loop [current                    nil
         acc                        {}
         [c & content* :as content] content]
    (if-not content
      acc
      (if (= :comment (:type c))
        (recur (get delimeters (:data c)) acc content*)
        (recur current (update acc current (fnil conj []) c) content*)))))

(defn content* [node]
  (if (map? node)
    (:content node)
    [node]))

(defn drop-ul-li [node]
  (as-> node %
    (if (= (:tag %) :ul) (:content %))
    (remove #{"\n"} %)
    (first %)
    (if (= (:tag %) :li) (:content %))
    (remove #{"\n"} %)))

(defn parse-inherited-fields [url html]
  "FIXME")

(defn parse-inherited-constructors [url html]
  "FIXME")

(def primitive-type?
  #{"boolean" "char" "byte" "int" "long" "float" "double" "void"})

(defn ->primitive-type [name]
  {:pre [(primitive-type? name)]}
  {:type       :java/type
   :primitive? true
   :name       name})

(defn- recursive-type-parse [url ^PeekPushBackIterator iter]
  (let [generic (.next iter)]
    (if (and (.hasNext iter)
             (= "<" (.peek iter)))
      (do (.next iter) ;; For side-effects
          (loop [acc []]
            (let [type (recursive-type-parse url iter)]
              (if (= ">" (.next iter))
                {:type       :java/generic
                 :class      generic
                 :parameters (conj acc type)}
                (recur (conj acc type))))))
      generic)))

(defn parse-type [url html]
  (let [tokens (->> html
                    (mapcat (fn [node]
                              (if (string? node)
                                (re-seq #"[,<>\s]|boolean|byte|char|int|long|float|double|void" node)
                                [node])))
                    (map #(if (primitive-type? %)
                            (->primitive-type %) %))
                    (map #(if-not (and (map? %)
                                       (= (:tag %) :a))
                            %
                            ;; FIXME (arrdem 2017-12-30):
                            ;;   Extract package name, other textual data?
                            (classref url %))))]
    (try
      (recursive-type-parse url (PeekPushBackIterator. (.iterator tokens)))
      (catch Exception e
        (throw (ex-info "Unable to parse signature of `:tokens`."
                        {:tokens tokens}
                        e))))))

(defn parse-method-signature [html]
  (let [tokens (->> html
                    (mapcat (fn [node]
                              (if (string? node)
                                (re-seq #"[,<>\s]|boolean|byte|char|int|long|float|double|void" node)
                                [node]))))]
    ))

(defn parse-method [url html]
  (let [[type description]   (->> (html/select html [:td])
                                  (map :content))
        type                 (-> type first :content)
        [method & signature] (-> description
                                 (html/select [:code])
                                 first
                                 :content)
        method               (-> method :content first)
        docs                 (html/select description [:div.block])]
    (try {:type        ::method
          :method      (methodref url method)
          :return-type (parse-type url type)
          :signature   (parse-method-signature signature)
          :docs        docs}
         (catch Exception e
           (throw (ex-info "Failed to parse method"
                           {:html      html
                            :signature signature
                            :docs      docs}
                           e))))))

(defn parse-inherited-methods [url html]
  (as-> html %
    (html/select % [:table #{:tr.altColor :tr.rowColor}])
    (map (partial parse-method url) %)))

(defn parse-javadoc-summary [url html]
  (let [summary-html (->> (html/select html [:div.summary])
                          (mapcat content*)
                          (mapcat content*)
                          (mapcat content*)
                          (remove #{"\n"}))]
    (as-> summary-html %
      (partition-by-comments summary-delimeters %)
      (map-vals % (partial mapcat drop-ul-li))
      #_(update % :fields (partial parse-inherited-fields url))
      #_(update % :constructors (paprtial parse-inherited-constructors url))
      (update % :methods (partial parse-inherited-methods url)))))

(defn parse-javadoc-details [url html]
  (let [details-html (html/select html [:div.details])]))

(defn parse-javadoc [url html]
  (let [{:keys [name supers]}             (parse-javadoc-inheritance url html)
        {inherited-methods :methods
         inherited-fields  :fields
         inherited-ctors   :constructors} (parse-javadoc-summary url html)]
    {:type         ::javadoc
     :url          url
     :name         name
     :supers       supers
     :constructors inherited-ctors
     :methods      inherited-methods
     :fields       inherited-fields}))

(defn parse-javadoc-stream [url ^InputStream stream]
  (parse-javadoc url (html/html-resource stream)))

(defn fetch-javadoc-for
  "Attempts to fetch the Javadocs for the given object or class.
  Returns a buffer of HTML as a string.

  If a `:local-roots` mapping matches, but the corresponding `.html`
  file does not exist, falls back to the `:remote-roots` mapping if any.

  If no options are provided, uses `#'*user-options*` which defaults
  to `#'default-options` unless modified or bound by the user.

  Programmatic access should pass options explicitly rather than rely
  on this behavior."
  ([class-or-object]
   (fetch-javadoc-for @*user-options* class-or-object))
  ([options class-or-object]
   (let [^Class c     (if (instance? Class class-or-object)
                        class-or-object
                        (class class-or-object))
         package-name (str (.getName (.getPackage c)) ".")
         class-name   (.getName c)]
     (or (if-let [local-package-url (javadoc-url-for-package* (:local-roots options) package-name)]
           (let [javadoc-url (javadoc-url-for-class local-package-url class-name)
                 file        (io/file javadoc-url)]
             (when (.exists file)
               (parse-javadoc-stream javadoc-url (FileInputStream. file)))))

         (if-let [remote-package-url (javadoc-url-for-package* (:remote-roots options) package-name)]
           (let [javadoc-url (javadoc-url-for-class remote-package-url class-name)]
             ;; FIXME (arrdem 2017-12-29):
             ;;    Try to hit in a cache first for gods sake
             (parse-javadoc-stream javadoc-url (fetch-url javadoc-url))))

         {:type    ::error
          :message "Could not find Javadoc for package"
          :package package-name
          :class   class-name
          :options options}))))
