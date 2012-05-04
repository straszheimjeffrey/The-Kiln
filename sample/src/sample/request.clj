(ns
    ^{:doc "Coals and Clays to Handle the Request"
      :author "Jeffrey Straszheim"}
  sample.request
  (use kiln.kiln
       slingshot.slingshot)
  (import java.net.URI))

(defcoal request
  "The Ring Request Object.")

(defclay path-as-vector
  "The path of the request split on /'s, to form a vec"
  :value (let [path (:uri (?? request))]
           (map (partial apply str)
                (partition-by #(= % \/) path))))

(defclay request-as-java-uri
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


;; End of file
