(ns
    ^{:doc "Generates a response for the sample server"
      :author "Jeffrey Straszheim"}
  sample.response
  (use [sample dispatch utils]
       [kiln-ring server request]
       kiln-kiln
       ring.util.response
       ring.util.servlet
       ring.middleware.params
       ring.middleware.keyword-params
       ring.middleware.session
       slingshot.slingshot
       hiccup.core
       clojure.tools.logging)
  (:gen-class))

;; Welcome.

;; To start, we need a main response clay.

(use 'clojure.pprint)
(declare log-glaze)

(defclay response-clay
  "The main Ring response."
  :value (do
           ;; The response is either a page or an action followed by a
           ;; redirect.
           (pprint request)
           (info (format "Begin Request: %s"
                         (-> request-uri ?? str)))
           (let [response
                 (condp = (?? response-type)
                     :redirect (do (when-let [action-to-run! (?? action!)]
                                     (?? action-to-run!))
                                   (redirect-after-post (-> redirect-uri ?? str)))
                     :page (response (?? page-to-show))
                     :not-found (not-found "Page not found"))]
             (if-let [new-session (?? new-session)]
               (assoc response :session new-session)
               response)))
  :glaze [(log-glaze :info)])

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
            ;; logon/logoff
            ]]))

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
