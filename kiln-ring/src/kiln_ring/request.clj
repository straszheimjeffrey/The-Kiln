(ns
    ^{:doc "Coals and Clays to Handle the Request"
      :author "Jeffrey Straszheim"}
  kiln-ring.request
  (use kiln.kiln
       kiln-ring.uri-utils
       slingshot.slingshot)
  (import java.net.URI))

(defcoal request
  "The Ring Request Object")

(defclay request-method
  "The request method, :get or :post"
  :value (-> request ?? :request-method))

(defclay remote-addr
  "The remote IP address"
  :value (-> request ?? :remote-addr))

(defclay scheme
  "The scheme, such as :http or :https"
  :value (-> request ?? :scheme))

(defclay headers
  "The headers, as a map of String -> String. If you pass a single
String argument, it will return that header."
  :args [& [which]]
  :value (let [header-map (-> request ?? :headers)]
           (if which
             (get header-map which)
             header-map)))

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
  "The request as a kiln-ring.uri-utils uri"
  :value (as-uri (?? request-uri-as-java-uri)))

(defclay params
  "The params from the request, as set by Ring middleware"
  :value (-> request ?? :params))

(defclay cookies
  "The cookies from the request, as set by Ring middleware"
  :value (-> request ?? :cookies))

(defclay session
  "The user session, as set by Ring middleware"
  :value (-> request ?? :session))


;; End of file
