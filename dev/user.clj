(ns user
  "Dev session helpers."
  {:authors ["Reid \"arrdem\" McKenzie <me@arrdem.com>"]
   :license "https://www.eclipse.org/legal/epl-v10.html"}
  (:require [stacks.server :refer []]))

(defonce SERVICE
  (atom nil))

(defn stop-web-server! [])

(defn start-web-server! [])

(defn start! []
  (start-web-server!)
  :ok)

(defn stop! []
  (stop-web-server!)
  :ok)

(defn restart! []
  (stop!)
  (start!))

(def reset! restart!)
