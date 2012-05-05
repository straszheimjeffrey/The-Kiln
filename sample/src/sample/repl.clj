(ns
    ^{:doc "Some simple tools to run Jetty in the repl"
      :author "Jeffrey Straszheim"}
  sample.repl
  (use slingshot.slingshot
       clojure.tools.logging
       [ring.adapter.jetty :only [run-jetty]]
       [ring.middleware.stacktrace :only [wrap-stacktrace-web]])
  (require [kiln-ring.server :as server])
  (import (org.apache.log4j Logger
                            Level
                            PatternLayout
                            FileAppender)))

(comment

(restart-server)

)

(defonce appender
  (FileAppender. (PatternLayout. "%d %-5p [%t] %F - %m%n")
                 "output.out"))

(defn- add-logging
  []
  (doto (Logger/getRootLogger)
    (.addAppender appender)
    (.setLevel Level/INFO)))

(defonce server nil)

(defn start-server
  [port]
  (add-logging)
  (when server
    (throw+ {:type :repl-error :message "Server already running"}))
  (alter-var-root
   #'server
   (fn [_]
     (run-jetty (wrap-stacktrace-web server/handler)
                {:port port
                 :join? false}))))

(defn stop-server
  []
  (when-not server
    (throw+ {:type :repl-error :message "Server not running"}))
  (alter-var-root
   #'server
   (fn [r]
     (.stop r)
     nil)))

(defn restart-server
  ([]
     (restart-server 4448))
  ([port]
     (when server
       (stop-server))
     (start-server port)))

;; End of file
