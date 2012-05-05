(ns
    ^{:doc "The main dispatcher of the sample server"
      :author "Jeffrey Straszheim"}
  sample.dispatch
  (use [sample logon-logoff message utils]
       [kiln-ring request]
       kiln.kiln
       slingshot.slingshot
       matchure
       clojure.tools.logging))


;; This is the main dispatch logic for a Kiln-Ring Restful Web
;; Server.

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


(declare path-as-seq
         main-matches
         logon-matches
         message-matches)

(defclay dispatch-request-method-and-path
  "A seq with the request request method followed by the path
components, such as:

  '(:get \"message\" \"54\")"
  :value (cons (?? request-method)
               (?? path-as-seq)))

(defclay main-dispatch
  "The main uri dispatch"
  :value (or (?? main-matches)
             (?? logon-matches)
             (?? message-matches)
             {:response-type :not-found}))

(defmacro dispatch-clay
  [name & forms]
  (let [form-pairs (partition 2 forms)]
    `(defclay ~name
       :value
       (cond-match
        ~@(->>
           (for [[pattern result] form-pairs]
             `([~pattern (~'?? dispatch-request-method-and-path)] ~result))
           (apply concat))))))

(dispatch-clay
 main-matches
 [:get "/"] {:response-type :redirect
             :action nil
             :redirect-uri (if (?? logged-on?)
                             list-messages-uri
                             logon-uri)})

(dispatch-clay
 logon-matches
 [:post "logon"] {:response-type :redirect
                  :action logon-action!
                  :new-session logon-new-session
                  :redirect-uri logon-redirect-uri}
 [:get "logon"] {:response-type :page
                 :title "Login"
                 :body logon-body}
 [:post "logoff"] {:response-type :redirect
                   :action logoff-action!
                   :new-session logoff-new-session
                   :redirect-uri logoff-redirect-uri})

(dispatch-clay
  message-matches
  [:get "list-messages"] {:response-type :page
                          :title "Messages"
                          :body list-messages-body}
  [:get "show-message" ?which] {:response-type :page
                                :title "Message"
                                :body show-message-body
                                :message-id which}
  [:get "new-message"] {:response-type :page
                        :title "New Message"
                        :body new-message-body}
  [:get "edit-message" ?which] {:response-type :page
                                :title "Edit Message"
                                :body edit-message-body
                                :message-id which}
  [:post "new-message"] {:response-type :redirect
                         :action new-message-action!
                         :redirect-uri new-message-redirect-uri}
  [:post "edit-message" ?which] {:response-type :redirect
                                 :action edit-message-action!
                                 :redirect-uri edit-message-redirect-uri
                                 :message-id which})

(defclay path-as-seq
  "The path of the request split on /'s, to form a vec"
  :value (let [path (:uri (?? request))]
           (->> (partition-by #(= % \/) path)
                (map (partial apply str))
                (remove #{"/"}))))


;; End of file
