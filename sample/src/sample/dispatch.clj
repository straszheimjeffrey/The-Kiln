(ns
    ^{:doc "The main dispatcher of the sample server"
      :author "Jeffrey Straszheim"}
  sample.dispatch
  (use [sample logon-logoff]
       [kiln-ring server request]
       kiln.kiln
       ring.util.response
       ring.util.servlet
       ring.middleware.params
       ring.middleware.keyword-params
       ring.middleware.session
       slingshot.slingshot
       hiccup.core
       matchure
       clojure.tools.logging)
  (:gen-class))

;; Welcome.

;; This is the main dispatch logic for a Kiln-Ring Restful Web
;; Server. In this code, I write from top to bottom to make it easy to
;; follow. Ignore the declare statements. They will be defined below.

;; To start, we need a main response clay.

(use 'clojure.pprint)
(declare action! redirect-uri page-to-show response-type new-session log-glaze)
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
                                     (?? action-to-run!)
                                     (redirect-after-post (-> redirect-uri ?? str))))
                     :page (response (?? page-to-show))
                     :not-found (not-found "Page not found"))]
             (if-let [new-session (?? new-session)]
               (assoc response :session new-session)
               response)))
  :glaze [(log-glaze :info)])

(declare main-dispatch)
(defclay response-type
  "How to respond"
  :value (-> main-dispatch ?? :response-type))

(defclay redirect-uri
  "The uri to redirect to."
  :value (-> main-dispatch ?? :redirect-uri ??))

(defclay action!
  "The action to perform."
  :value (-> main-dispatch ?? :action))

(defclay new-session
  "Any update to the session"
  :value (when-let [ns (-> main-dispatch ?? :new-session)]
           (?? ns)))

(defclay page-title
  "The title to show."
  :value (-> main-dispatch ?? :title))

(defclay page-body
  "The main body of the page"
  :value (-> main-dispatch ?? :body ??))

(declare path-as-seq error-body)
(defclay main-dispatch
  "The main uri dispatch"
  :value
  (let [path (cons (?? request-method)
                   (?? path-as-seq))]
    (cond-match
     [[:post "logon"] path] {:response-type :redirect
                             :action logon-action!
                             :new-session logon-new-session
                             :redirect-uri logon-redirect-uri}
     [[:get "logon"] path] {:response-type :page
                            :title "Login"
                            :body logon-body}
     [[:post "logoff"] path] {:response-type :redirect
                              :action logoff-action!
                              :new-session logoff-new-session
                              :redirect-uri logoff-redirect-uri}
     [_ path] {:response-type :not-found})))


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

(declare main-uri)
(defclay page-footer
  "The page footer"
  :value (html
          [:div#footer
           [:p
            [:a {:href (-> main-uri ?? str)} "(home)"]
            ;; logon/logoff
            ]]))

(defclay main-uri
  "The main page uri"
  :value (-> (?? request-uri)
             (assoc :path "/"
                    :query nil)))
  
(defclay path-as-seq
  "The path of the request split on /'s, to form a vec"
  :value (let [path (:uri (?? request))]
           (->> (partition-by #(= % \/) path)
                (map (partial apply str))
                (remove #{"/"}))))

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
