(ns
    ^{:doc "Some simple tools to run Jetty in the repl"
      :author "Jeffrey Straszheim"}
  sample.repl
  (use slingshot.slingshot
       clojure.tools.logging
       [ring.adapter.jetty :only [run-jetty]]
       [ring.middleware.stacktrace :only [wrap-stacktrace-web]])
  (require (sample [response :as response]))
  (import (org.apache.log4j Logger
                            Level
                            PatternLayout
                            FileAppender)))


;; This is some utility code I use to run Ring/Jetty from within my
;; REPL. It is useful. Copy it. Steal it from me!

;; It sets up some basic logging in Log4j.

(comment

(restart-server)

)

(defonce appender
  (FileAppender. (PatternLayout. "%d %-5p [%t] - %m%n")
                 "output.out"))

(defn- add-logging
  []
  (doto (Logger/getRootLogger)
    (.addAppender appender)
    (.setLevel Level/INFO)))

(defonce server nil)

#_(use 'clojure.pprint)
#_(defn- wrap-inspect
  [handler]
  (fn [request]
    (println "Request")
    (pprint request)
    (let [res (handler request)]
      (print "Response")
      (pprint res)
      res)))

(defn start-server
  [port]
  (add-logging)
  (when server
    (throw+ {:type :repl-error :message "Server already running"}))
  (alter-var-root
   #'server
   (fn [_]
     (run-jetty (wrap-stacktrace-web response/handler)
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
