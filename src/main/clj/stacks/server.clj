(ns stacks.server
  "A webserver for serving content rendered with Stacks.

  Lots of cribbing from Grimoire here. Surprise."
  {:authors ["Reid \"arrdem\" McKenzie <me@arrdem.com>"]
   :license "https://www.eclipse.org/legal/epl-v10.html"}
  (:require [clojure.string :as str]
            [clojure.java.io :as io]
            [clojure.set :refer [map-invert]]

            [compojure.core :refer [defroutes GET]]
            [compojure.handler :as handler]
            [ring.adapter.jetty :as jetty]
            [hiccup.core :refer [html]]
            [hiccup.page :as page]

            ;; Ring stuff
            [ring.middleware.resource :refer [wrap-resource]]
            [ring.middleware.session :refer [wrap-session]]
            [ring.middleware.file :refer [wrap-file]]
            [ring.util.response :refer [redirect]]

            [stacks.tools.projects :refer [project->doctree]]
            [stacks.tools.source-tree :refer [doctree->source-tree]]
            [stacks.tools.pygments :refer [pygmentize-file]]

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
            :reload)
  (:import java.io.File))

(def +article-parsing-middleware+
  (-> handle-parse-block
      handle-parse-sessions
      #_handle-parse-var-links
      #_handle-parse-spec-links))

(def +article-rendering-middleware+
  (-> handle-render-block
      handle-render-sessions
      handle-render-pygmentize
      handle-render-graphviz
      #_handle-render-var-links
      #_handle-render-spec-links))

(def +file-ext-pattern+
  #"\.[\w\d]*?$")

(def +project+
  {:source-paths ["src/main/clj"
                  "src/main/cljc"
                  "src/dev/clj"
                  "src/dev/cljc"]
   :test-paths ["src/test/clj"
                "src/test/cljc"]
   :doc-paths ["doc"
               "README.md"]})

(defonce +project-doctree+
  (project->doctree +project+))

(defn doctree->toctree
  "Given a doctree and the address of an item in the doctree, render a
  sidebar table highlighting the currently active page."
  [doctree & [active?]]
  (list
   [:h2 "Articles"]
   [:ul {}
    (for [{:keys [file content] :as f} (:docs doctree)
          :let [path* (.getPath ^File file)
                path (str "/" path*)]]
      [:li [:a {:href path}
            (cond->> (:title content)
              (= path* active?) (vector :b))]])]
   [:h2 "Namespaces"]
   [:div.source-tree
    (let [sources->paths (zipmap (map :content (:sources doctree))
                                 (map #(str "/" (.getPath (:file %))) (:sources doctree)))]
      (doctree->source-tree
       sources->paths
       (map :content (:sources doctree))
       (get (map-invert sources->paths) active?)))]))

(defn layout
  "Lay out content into a page with a doctree sidebar."
  [content & [active? title?]]
  (html
   [:head
    (for [size [57 60 72 76 114 120 144 152 180]
          :let [dim (format "%1$sx%1$s" size)]]
      [:link {:rel "apple-touch-icon",
              :sizes dim,
              :href (format "/ico/apple-icon-%s.png" dim)}])

    (for [size [36 48 72 96 144 192]
          :let [dim (format "%1$sx%1$s" size)]]
      [:link {:rel "icon"
              :type "image/png",
              :sizes dim,
              :href (format "/ico/android-icon-%s.png" dim)}])

    [:link {:rel "manifest", :href "/manifest.json"}]

    [:meta {:name "msapplication-TileColor", :content "#ffffff"}]
    [:meta {:name "msapplication-TileImage", :content "/ico/ms-icon-144x144.png"}]
    [:meta {:name "theme-color", :content "#ffffff"}]

    (page/include-css "/css/normalize.css")
    (page/include-css "/css/skeleton.css")

    ;; My CSS
    (page/include-css "/css/default.css")

    ;; CSS for articles (can I selectively include / inject this?)
    (page/include-css "/css/articles/default.css")

    (page/include-css "/css/session/default.css")
    (page/include-css "/css/pygments/default.css")
    #_(page/include-css "/css/pygments/codeschool.css")

    (when title?
      [:title title? "- Stacks"])]
   [:body#body
    [:div#sidebar
     [:h1 [:a {:href "/"} "Stacks"]]
     [:hr]
     (doctree->toctree +project-doctree+ active?)]
    [:div#content
     content]]
   [:foot
    ]))

(defroutes app
  (GET "/" []
    (redirect "/article/README"))

  (GET "/article/:article" [article]
    (if-let [source (or (let [f (io/file (str article ".md"))]
                          (if (.exists f) f))
                        (let [f (io/file (str "doc/" article ".md"))]
                          (if (.exists f) f))
                        #_(io/resource (str article ".md")))]
      (let [article (articles/parse-article +article-parsing-middleware+ source)]
        (layout (articles/render-article +article-rendering-middleware+ article)
                (.getPath ^File source)
                (:title article)))))

  ;; Rewrite doc/ to articles
  (GET "/doc/:file" [file]
    (redirect (str "/article/" (str/replace file +file-ext-pattern+ ""))))

  ;; Rewrite markdown files to articles
  (GET ["/:path" :path #".*?\.(md|markdown)$"] [path]
    (redirect (str "/article/" (str/replace path +file-ext-pattern+ ""))))

  (GET "/:path{.*}" [path]
    (let [f (io/file path)]
      (if (.exists f)
        (layout (pygmentize-file f) path)))))

(defn log-requests [handler]
  (fn [request]
    (prn (select-keys request [:request-method :uri :remote-addr :query-string :headers]))
    (handler request)))

(defonce +server+
  (atom nil))

(defn start-web-server! [& [port?]]
  (let [jetty-cfg {:port  (or port? 3000)
                   :host  "0.0.0.0"
                   :join? false}
        jetty     (-> app
                      handler/site
                      log-requests
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
