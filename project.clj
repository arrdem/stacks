(defproject me.arrdem/stacks "_"
  :description "Stacks of docs - sketches at better documentation tools."
  :url "https://github.com/arrdem/stacks"
  :license {:name "Eclipse Public License"
            :url  "http://www.eclipse.org/legal/epl-v10.html"}

  :source-paths      ["src/main/clj"
                      "src/main/cljc"]
  :java-source-paths ["src/main/jvm"]
  :resource-paths    ["src/main/resources"]

  :dependencies [[org.clojure/clojure "1.9.0"]
                 [org.clojure/java.classpath "0.2.3"]
                 [me.arrdem/commonmark-hiccup "0.1.1"]
                 [me.arrdem/detritus "0.3.3"]
                 [me.arrdem/microfiche "0.1.0"]]

  :profiles
  {:test {:test-paths     ["src/test/clj"
                           "src/test/cljc"]
          :resource-paths ["src/test/resources"]}

   :dev {:dependencies [;; For the server, can this be a different artifact?
                        [ring/ring-core "1.6.3"]
                        [ring/ring-jetty-adapter "1.6.3"]
                        #_[selmer "1.11.3"
                           :exclude [hiccups]]
                        [compojure "1.6.0"]
                        [hiccup "1.0.5"]
                        [cheshire "5.8.0"]]}}

  :plugins [[me.arrdem/lein-git-version "2.0.3"]]

  :git-version
  {:status-to-version
   (fn [{:keys [tag version ahead ahead? dirty?] :as git}]
     (if (and tag (not ahead?) (not dirty?))
       tag
       (str tag
            (when ahead? (str "." ahead))
            (when dirty? "-SNAPSHOT"))))})
