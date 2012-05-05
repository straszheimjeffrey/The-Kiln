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

(use 'clojure.pprint)
(declare redirect-response page-to-show log-glaze)

(defclay response-clay
  "The main Ring response."
  :value (do
           ;; The response is either a page or an action followed by a
           ;; redirect.
           (pprint (?? request))
           (info (format "Begin Request: %s"
                         (-> request-uri ?? str)))
           (try+
             (let [response (condp = (?? response-type)
                              :redirect (?? redirect-response)
                              :page (response (?? page-to-show))
                              :not-found (not-found "Page not found"))]
               (if-let [new-session (?? new-session)]
                 (assoc response :session new-session)
                 response))
             (catch [:type :forced-redirect] {:keys [uri]}
               (info (format "Forced redirect to %s" (str uri)))
               (redirect-after-post (str uri)))
             (catch [:type :error-page] {:keys [message]}
               ;; for now
               (response (format "Error: %s" message)))))
  :glaze [(log-glaze :info)])

(defclay redirect-response
  :value (do (when-let [action-to-run! (?? action!)]
               (?? action-to-run!))
             (redirect-after-post (-> redirect-uri ?? str))))

(declare page-header page-footer)

(defclay page-to-show
  "The page"
  :value (html
          [:html
           [:head
            [:title (-> page-title ?? h)]]
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

(defglaze log-glaze
  :args [log-level]
  :operation (let [clay-name (:name ?clay)]
               (log log-level (format "Running %s" clay-name))
               (let [result (?next)]
                 (log log-level (format "Completed %s: %s"
                                        clay-name
                                        (pr-str result)))
                 result)))
                
(defn- on-error
  [exc kiln]
  (error exc "Exception in Kiln")
  (if (instance? Exception kiln)
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
