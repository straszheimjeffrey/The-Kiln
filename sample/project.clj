(defproject kiln/sample "0.0.1"
  :description "A sample project showing Kiln over Ring"
  :dependencies [[org.clojure/clojure "1.3.0"]
                 [kiln/kiln-ring "1.1.1"]
                 [matchure "0.10.1"]
                 [hiccup "1.0.0"]
                 [org.clojure/tools.logging "0.2.3"]
                 [log4j "1.2.16" :exlusions [javax.mail/mail
                                             javax.jms/jms
                                             com.sun.jdmk/jmxtools
                                             com.sun.jmx/jmxri]]]
  :dev-dependencies [[swank-clojure "1.3.3"]]
  :aot [#".*"]
  :ring {:handler sample.response/handler})

;; End of file
