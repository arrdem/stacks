(ns stacks.server
  ""
  {:authors ["Reid \"arrdem\" McKenzie <me@arrdem.com>"]
   :license "https://www.eclipse.org/legal/epl-v10.html"}
  (:require [stacks.doctree :as doctree]
            [compojure.core :as comp]
            [compojure.handler :as handler]
            [compojure.middleware :refer [wrap-canonical-redirect]]
            [ring.adapter.jetty :as jetty]
            [cheshire.core :refer [encode]]
            [ring.middleware.resource :refer [wrap-resource]]
            [ring.middleware.session :as session]
            [clojure.java.io :as io]))

(comp/defroutes app
  (comp/GET "/" []
    (io/resource "_html/index.html"))
  ())


(defn start-web-server! [& [port? file?]]
  (let [jetty-cfg {:port  (or port? 3000)
                   :host  "127.0.0.1"
                   :join? false}
        jetty     (-> app
                      handler/site
                      wrap-session
                      wrap-resource
                      wrap-canonical-redirect
                      (jetty/run-jetty jetty-cfg))]

    (println "starting!" jetty-cfg)

    ;; Return nil b/c side-effects
    jetty))

(defn -main [& [port?]]
  ;; Boot the webserver
  (start-web-server!
   (if (string? port?)
     (Long. port?)
     3000))

  ;; Return nil b/c side-effects
  nil)
