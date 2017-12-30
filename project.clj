(defproject me.arrdem/stacks "_"
  :description "Stacks of docs - sketches at better documentation tools."
  :url "https://github.com/arrdem/stacks"
  :license {:name "Eclipse Public License"
            :url  "http://www.eclipse.org/legal/epl-v10.html"}

  :dependencies [[org.clojure/clojure "1.8.0"]
                 ;; FIXME: are these actually useed?
                 [org.clojure/tools.reader "1.1.1"]
                 [org.clojure/tools.namespace "0.2.11"]
                 [org.clojure/tools.analyzer.jvm "0.7.0"]

                 ;; Used for sessions & articles
                 [rewrite-clj/rewrite-clj "0.6.0"]
                 [me.arrdem/commonmark-hiccup "LATEST"]
                 [me.arrdem/detritus "LATEST"]

                 ;; For the server, can this be a different artifact?
                 [ring/ring-core "1.6.3"]
                 [ring/ring-jetty-adapter "1.6.3"]
                 [selmer "1.11.3"]
                 [compojure "1.6.0"]
                 [hiccup "1.0.5"]
                 [cheshire "5.8.0"]
                 [instaparse "1.4.8"] ;; FIXME: what needs this and why?
                 ]

  :plugins [[me.arrdem/lein-git-version "LATEST"]]
  :git-version {:status-to-version
                (fn [{:keys [tag version ahead ahead? dirty?] :as git}]
                  (if (and tag (not ahead?) (not dirty?))
                    tag
                    (str tag
                         (when ahead? (str "." ahead))
                         (when dirty? "-SNAPSHOT"))))})
