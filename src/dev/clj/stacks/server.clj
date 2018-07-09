(ns stacks.server
  "A webserver for serving content rendered with Stacks.

  Lots of cribbing from Grimoire here. Surprise."
  {:authors ["Reid \"arrdem\" McKenzie <me@arrdem.com>"]
   :license "https://www.eclipse.org/legal/epl-v10.html"}
  (:require [compojure.core :refer [defroutes GET]]
            [compojure.handler :as handler]
            [ring.adapter.jetty :as jetty]
            [cheshire.core :refer [encode]]
            [hiccup.core :refer [html]]
            [hiccup.page :as page]
            [ring.middleware.resource :refer [wrap-resource]]
            [ring.middleware.session :refer [wrap-session]]
            [clojure.java.io :as io]
            ;; Articles, Article handlers
            [stacks.tools.articles :as articles
             :refer [handle-parse-block handle-render-block]]
            [stacks.tools.articles.middleware.sessions
             :refer [handle-parse-sessions handle-render-sessions]]
            [stacks.tools.articles.middleware.pygmentize
             :refer [handle-render-pygmentize]]
            [stacks.tools.articles.middleware.graphviz
             :refer [handle-render-graphviz]]

            ;; For development, force reloading
            :reload))

(def +article-parsing-middleware+
  (-> handle-parse-block
      handle-parse-sessions))

(def +article-rendering-middleware+
  (-> handle-render-block
      handle-render-sessions
      handle-render-pygmentize
      handle-render-graphviz))

(defroutes app
  (GET "/" []
    (io/resource "_html/index.html"))
  (GET "/article/:article" [article]
    (as-> (io/file (str "doc/" article ".md")) %
      (articles/parse-article +article-parsing-middleware+ %)
      (articles/render-article +article-rendering-middleware+ %)
      (html
       [:head
        (page/include-css "/css/articles/default.css")
        (page/include-css "/css/pygments/default.css")
        #_(page/include-css "/css/pygments/codeschool.css")]
       [:body %]))))

(defonce +server+
  (atom nil))

(defn start-web-server! [& [port?]]
  (let [jetty-cfg {:port  (or port? 3000)
                   :host  "127.0.0.1"
                   :join? false}
        jetty     (-> app
                      handler/site
                      wrap-session
                      (wrap-resource "_static")
                      (jetty/run-jetty jetty-cfg))]

    (reset! +server+ jetty)

    (println "starting!" jetty-cfg)

    ;; Return nil b/c side-effects
    jetty))

(defn stop-web-server! []
  (when-let [server* @+server+]
    (.stop server*)
    (reset! +server+ nil)
    ;; Return nil b/c side-effects
    nil))

(defn restart-web-server! [& [port?]]
  (stop-web-server!)
  (start-web-server! port?)

  ;; Return nil b/c side-effects
  nil)

(when @+server+
  (restart-web-server!))

(defn -main [& [port?]]
  ;; Boot the webserver
  (reset! +server+
          (start-web-server!
           (if (string? port?)
             (Long. port?)
             3000)))

  ;; Return nil b/c side-effects
  nil)
