(ns
    ^{:doc "Generates a response for the sample server"
      :author "Jeffrey Straszheim"}
  sample.response
  (use kiln.kiln
       ring.util.response
       ring.util.servlet
       ring.middleware.params
       ring.middleware.keyword-params
       ring.middleware.session
       slingshot.slingshot
       hiccup.core
       clojure.tools.logging)
  (require (sample [dispatch :as dispatch]
                   [logon-logoff :as logon]
                   [utils :as utils])
           (kiln-ring [server :as server]
                      [request :as request]))
  (:gen-class))


;; Welcome!

;; I assume you are just beginning with this code. I have tried to
;; make it very easy to follow and understand. I hope that I
;; succeed. Let's Go!

;; First, you'll see a lot of declare statements like this one. Just
;; ignore them for now.
(declare redirect-response page-to-show)


;; To start, we need a main response clay. This is the main entry
;; point of our code.
(defclay response-clay
  "The main Ring response."
  :glaze [(utils/log-glaze :info)]
  
  :value (do

           ;; This is the start, so let's log something. (Note how I
           ;; use the -> with the ??. Arrows rule!)
           (info (format "Begin Request: %s"
                         (-> request/request-uri ?? str)))
           
           (try+
            
            ;; Our application has three basic types of responses: we
            ;; can show a page, we can do some action followed by a
            ;; redirect, or we can show a "Not Found" page. We
            ;; determine which by inspecting the response-type
            ;; clay. Depending on which we have, we get the result
            ;; from a different clay.
            (let [response (condp = (?? dispatch/response-type)
                             :page (response (?? page-to-show))
                             :redirect (?? redirect-response)
                             :not-found (not-found "Page not found"))]

              ;; We may need to update the session. If so, it will be
              ;; in the new-session clay.
              (if-let [new-session (?? dispatch/new-session)]
                (assoc response :session new-session)
                response))
            
            ;; These are a couple short-circuit operation handled by
            ;; exceptions.
            (catch [:type :forced-redirect] {:keys [uri]}
              (info (format "Forced redirect to %s" (str uri)))
              (redirect-after-post (str uri)))
            (catch [:type :error-page] {:keys [message]}
              ;; for now
              (response (format "Error: %s" message))))))


;; For a redirect-response, we check the action! clay, which will
;; return nil or another clay. If we find a clay, we evaluate it. Then
;; we redirect to whatever is in redirect-uri. Notice again how I use
;; the clojure -> operator to call a clay. I do that a lot. Arrows
;; rule!
(defclay redirect-response
  :value (do (when-let [action-to-run! (?? dispatch/action!)]
               (?? action-to-run!))
             (redirect-after-post (-> dispatch/redirect-uri ?? str))))

(declare page-header page-footer stylesheet)

;; For a page, we put it together from these clays: page-title,
;; stylesheet, page-header, page-body, page-footer. The stylesheet,
;; header, and footer are defined in this module. The title and body
;; are defined in the sample.dispatch module, as they depend on how we
;; dispatch.
(defclay page-to-show
  "The page"
  :value (html
          [:html
           [:head
            [:title (-> dispatch/page-title ?? h)]
            [:style {:type "text/css"}
             (?? stylesheet)]]
           [:body
            (?? page-header)
            [:div#main (?? dispatch/page-body)]
            (?? page-footer)]]))

;; The header is really simple.
(defclay page-header
  "The page header"
  :value (html
          [:div#header
           [:h1 (-> dispatch/page-title ?? h)]]))


;; The footer is a bit more complex. It adds links. The application
;; uri's, by the way, are defined in the module sample.utils. I like
;; to keep them in one place. Also notice that we ask if the user is
;; logged-on?, which is defined in the sample.logon-logoff module.
(defclay page-footer
  "The page footer"
  :value (html
          [:div#footer
           [:p
            [:a {:href (-> utils/root-uri ?? str)} "(home)"]
            " "
            (if (?? logon/logged-on?)
              [:a {:href (-> utils/logoff-uri ?? str)} "(logoff)"]
              [:a {:href (-> utils/logon-uri ?? str)} "(logon)"])]]))

;; The stylesheet. What a boring clay!
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
h2 {font-size: 1.1em;
    color: #333;
    margin: 0.25in 0in;
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
.error {color: #533;
}
form input[type=text], form input[type=password], textarea
 {width: 6in;
 }

ul {margin: 0.25in 0in;
}
li {margin: 0.2in 0in;
}
.header {font-size: 1.1em;
             color: #222
}
.owner {font-size: 0.9em;
}
")

;; That's it for this. The stuff below is Ring plumbing.



;; Below are the functions to interface with the Kiln-Clay
;; library, which are documented there. Notice that we use some basic
;; Ring wrappers. Also, we have custom error handling.

;; The one thing worth noticing is that we pass response-clay, which
;; we said was our main entry point, on to the kiln-handler.

(defn- on-error
  [exc kiln]
  (error exc "Exception in Kiln")
  (if (instance? Exception exc)
    ;; Handle java.lang.Exception gracefully
    (do (cleanup-kiln-failure kiln)
        (-> (response "An error occurred")
            (status 500)))
    ;; Go boom for a Throwable
    (throw exc)))
            
(server/apply-kiln-handler response-clay
                           :on-error on-error
                           :middleware [wrap-keyword-params
                                        wrap-params
                                        wrap-session])

(def handler server/handler)
(servlet handler)



;; End of file
