(ns
    ^{:doc "The main dispatcher of the sample server"
      :author "Jeffrey Straszheim"}
  sample.dispatch
  (use [kiln-ring server request]
       kiln.kiln
       ring.util.response
       ring.util.servlet
       ring.middleware.params
       ring.middleware.keyword-params
       ring.middleware.session
       ring.middleware.session.cookie
       slingshot.slingshot
       hiccup.core
       matchure)
  (:gen-class))

;; Welcome.

;; This is the main dispatch logic for a Kiln-Ring Restful Web
;; Server. In this code, I write from top to bottom to make it easy to
;; follow. Ignore the declare statements. They will be defined below.

;; To start, we need a main response clay.

(use 'clojure.pprint)
(declare action! redirect-uri page-to-show response-type new-session)
(defclay response-clay
  "The main Ring response."
  :value (do
           (pprint (?? request))
           ;; The response is either a page or an action followed by a
           ;; redirect.
           (let [response
                 (condp (= (?? response-type))
                     :redirect (do (when-let [action-to-run! (?? action!)]
                                     (?? action-to-run!)
                                     (redirect-after-post (?? redirect-uri))))
                     :page (response (?? page-to-show)))]
             (if-let [new-session (?? new-session)]
               (assoc response :session new-session)
               response))))
             

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
  :value (-> main-dispatch ?? :new-session))

(defclay page-title
  "The title to show."
  :value (-> main-dispatch ?? :title))

(declare page-title page-header page-body page-footer)
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

            

(declare path-as-seq)
(defclay main-dispatch
  "The main uri dispatch"
  :value
  (let [path (cons (?? request-type)
                   (??path-as-seq))]
    (cond-match
     [[:post "login"] path] {:response-type :redirect
                             :action login-action!
                             :new-session login-new-session
                             :redirect-uri login-redirect-uri}
     [[:get "login"] path] {:response-type :page
                            :title "Login"
                            :body login-body}
     [[:post "logoff"] path] {:response-type :redirect
                              :action logoff-action!
                              :new-session logoff-new-session
                              :redirect-uri logoff-redirect-uri})))
  
(defclay path-as-seq
  "The path of the request split on /'s, to form a vec"
  :value (let [path (:uri (?? request))]
           (->> (partition-by #(= % \/) path)
                (map (partial apply str))
                (remove #{"/"}))))
                



             
(defonce session-store (cookie-store))

(apply-kiln-handler response-clay
                    :middleware [wrap-keyword-params
                                 wrap-params
                                 #(wrap-session % {:store session-store})])

(servlet handler)

;; End of file
