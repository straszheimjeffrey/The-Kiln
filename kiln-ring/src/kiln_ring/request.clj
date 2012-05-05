(ns
    ^{:doc "Coals and Clays to Handle the Request"
      :author "Jeffrey Straszheim"}
  kiln-ring.request
  (use kiln.kiln
       kiln-ring.uri-utils
       slingshot.slingshot)
  (import java.net.URI))

(defcoal request
  "The Ring Request Object.")

(defclay path-as-vector
  "The path of the request split on /'s, to form a vec"
  :value (let [path (:uri (?? request))]
           (map (partial apply str)
                (partition-by #(= % \/) path))))

(defclay request-uri-as-java-uri
  "The request data as a java uri"
  :value (let [{:keys [scheme
                       server-name
                       server-port
                       uri
                       query-string]} (?? request)]
           (URI. (name scheme)
                 nil
                 server-name
                 server-port
                 uri
                 query-string
                 nil)))

(defclay request-uri
  "The request as a kiln-ring.uri-utils uri."
  :value (as-uri (?? request-uri-as-java-uri)))


;; End of file
