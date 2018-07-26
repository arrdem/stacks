(ns user
  "Dev session helpers."
  {:authors ["Reid \"arrdem\" McKenzie <me@arrdem.com>"]
   :license "https://www.eclipse.org/legal/epl-v10.html"}
  (:require [stacks.server
             :refer [start-web-server!
                     stop-web-server!
                     restart-web-server!]]))

