(ns
    ^{:doc "Generates a response for the sample server"
      :author "Jeffrey Straszheim"}
  sample.response
  (use [sample dispatch logon-logoff utils]
       [kiln-ring server request]
       kiln.kiln
       ring.util.response
       ring.util.servlet
       ring.middleware.params
       ring.middleware.keyword-params
       ring.middleware.session
       slingshot.slingshot
       hiccup.core
       clojure.tools.logging)
  (:gen-class))

;; Welcome!

;; To start, we need a main response clay.

(declare redirect-response page-to-show)

(defclay response-clay
  "The main Ring response."
  :glaze [(log-glaze :info)]
  
  :value (do
           (info (format "Begin Request: %s"
                         (-> request-uri ?? str)))
           (try+
            
            ;; The response is either a page or an action followed by
            ;; a redirect.
            (let [response (condp = (?? response-type)
                             :redirect (?? redirect-response)
                             :page (response (?? page-to-show))
                             :not-found (not-found "Page not found"))]
              (if-let [new-session (?? new-session)]
                
                ;; We may need to update the session
                (assoc response :session new-session)
                response))
            
            ;; These are a couple short-circuit operation handled by
            ;; exceptions
            (catch [:type :forced-redirect] {:keys [uri]}
              (info (format "Forced redirect to %s" (str uri)))
              (redirect-after-post (str uri)))
            (catch [:type :error-page] {:keys [message]}
              ;; for now
              (response (format "Error: %s" message))))))
  
(defclay redirect-response
  :value (do (when-let [action-to-run! (?? action!)]
               (?? action-to-run!))
             (redirect-after-post (-> redirect-uri ?? str))))

(declare page-header page-footer stylesheet)

(defclay page-to-show
  "The page"
  :value (html
          [:html
           [:head
            [:title (-> page-title ?? h)]
            [:style {:type "text/css"}
             (?? stylesheet)]]
           [:body
            (?? page-header)
            [:div#main (?? page-body)]
            (?? page-footer)]]))

(defclay page-header
  "The page header"
  :value (html
          [:div#header
           [:h1 (-> page-title ?? h)]]))

(defclay page-footer
  "The page footer"
  :value (html
          [:div#footer
           [:p
            [:a {:href (-> root-uri ?? str)} "(home)"]
            " "
            (if (?? logged-on?)
              [:a {:href (-> logoff-uri ?? str)} "(logoff)"]
              [:a {:href (-> logon-uri ?? str)} "(logon)"])]]))

(defclay stylesheet
  "The CSS text"
  :value
  "
* {margin: 0px;
   padding: 0px;
   list-style: none;
}
body {margin: 0in .75in;
      color: #111;
      font-size: large;
}
div {margin: .25in 0in;
}
h1 {font-size: 1.3em;
}
a, a:visited {color: #225;
              text-decoration: none;
              font-size: 0.8em;
}
a:hover {color: #448;
}
form p {margin: 5px 0px;
}
p { margin: 0.25in 0px;
}
form input[type=text], form input[type=password], textarea
 {width: 6in;
 }

ul {margin: .25in 0in;
}
li {margin: 0.1in 0in;
}
.header {font-size: 1.1em;
             color: #222
}
.owner {font-size: 0.9em;
}
")

(defn- on-error
  [exc kiln]
  (error exc "Exception in Kiln")
  (if (instance? Exception exc)
    ;; Handle java.lang.Exception gracefully
    (do (cleanup-kiln-failure)
        (-> (response "An error occurred")
            (status 500)))
    ;; Go boom for a Throwable
    (throw exc)))
            
(apply-kiln-handler response-clay
                    :on-error on-error
                    :middleware [wrap-keyword-params
                                 wrap-params
                                 wrap-session])

(servlet handler)



;; End of file
