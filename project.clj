(defproject me.arrdem/stacks "0.1.0-SNAPSHOT"
  :description "Stacks of docs - sketches at better documentation tools."
  :url "https://github.com/arrdem/stacks"
  :license {:name "Eclipse Public License"
            :url  "http://www.eclipse.org/legal/epl-v10.html"}

  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/tools.reader "1.1.1"]
                 [rewrite-clj/rewrite-clj "0.6.0"]
                 [me.arrdem/commonmark-hiccup "LATEST"]
                 [me.arrdem/detritus "LATEST"]]

  :plugins [[me.arrdem/lein-git-version "LATEST"]]
  :git-version {:status-to-version
                (fn [{:keys [tag version ahead ahead? dirty?] :as git}]
                  (if (and tag (not ahead?) (not dirty?))
                    tag
                    (str tag
                         (when ahead? (str "." ahead))
                         (when dirty? "-SNAPSHOT"))))})
