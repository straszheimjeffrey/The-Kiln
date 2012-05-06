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

;; The thing to notice in this code is this: it does not evaluate any
;; business logic clay. It will use request oriented clays, such as
;; the request-method or the uri. But clays that actually do the
;; application work are not called. The reason for this is simple: we
;; have to calculate the dispatch data *before* we can compute the
;; business logic, since the business logic depends on it.


;; I define main-dispatch later. For now, know that it returns a map
;; that contains data and clays.
(declare main-dispatch)


;; First are some basic data clays. You saw how some of these get used
;; in sample.response. Notice how they all get their data from the
;; main-dispatch map.

(defclay response-type
  "How to respond. :page, :redirect, or :not-found."
  :value (-> main-dispatch ?? :response-type))

;; Redirect-uri, when called, runs its clay.
(defclay redirect-uri
  "If this is a redirect, where should we go? Returns a
kiln-ring.uri-utils.uri."
  :value (-> main-dispatch ?? :redirect-uri ??))

;; Action! does not run its clay!
(defclay action!
  "The action to perform. A clay."
  :value (-> main-dispatch ?? :action))

(defclay new-session
  "A new session, if it changes. Or else nil."
  :value (when-let [ns (-> main-dispatch ?? :new-session)]
           (?? ns)))

(defclay page-title
  "The title to show."
  :value (-> main-dispatch ?? :title))

;; The HTML text of the main body
(defclay page-body
  "The main body of the page, HTML text."
  :value (-> main-dispatch ?? :body ??))


;; Next, we are going to crack apart the request and make a nice
;; object to dispatch on. What we want is to turn a request like this:

;; http://www.example.com/fred/mary/sue

;; into this

;; [:get "fred" "mary" "sue"]

;; Obviously that will be :post if the request was a post.

(defclay path-as-seq
  "The path of the request split on /'s, to form a seq."
  :value (let [path (:uri (?? request))]
           (->> (partition-by #(= % \/) path)
                (map (partial apply str))
                (remove #{"/"}))))

(defclay dispatch-request-method-and-path
  "A seq with the request request method followed by the path
components, such as:

  '(:get \"message\" \"54\")"
  :value (cons (?? request-method)
               (?? path-as-seq)))


;; Almost ready for the hard parts!

(declare main-matches
         logon-matches
         message-matches)

;; We break the dispatches in to three groups: main, logon, and
;; message. This is just to make the code easier to keep track of.

(defclay main-dispatch
  "The main uri dispatch"
  :value (or (?? main-matches)
             (?? logon-matches)
             (?? message-matches)
             {:response-type :not-found}))


;; We're going to use the Matchure library to actually select and bind
;; the request data. Since the Matcure syntax is (shall we say)
;; verbose, I have built a convinience macro to make the dispatcher
;; easier to follow. Like most macros, it is tricky (look what I did
;; with the ~'??), but I believe the actual dispatch code is easy to
;; read.

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


;; Only one of these. It matches a URI without a path.
(dispatch-clay
 main-matches
 ;; For an empty path, redirect them to either the logon page, or the
 ;; message list page, depending on if they are logged on.
 [:get] {:response-type :redirect
         :action nil
         :redirect-uri (if (?? logged-on?)
                         list-messages-uri
                         logon-uri)})


;; Logon and logoff. This is more interesting. Here we have action and
;; body clays such as logon-action! and logon-body, which are defined
;; in the sample.logon-logoff module. Note we are not evaluating them
;; here, only setting them into a map.
(dispatch-clay
 logon-matches
 [:post "logon"] {:response-type :redirect
                  :action logon-action!
                  :new-session logon-new-session
                  :redirect-uri logon-redirect-uri}
 [:get "logon"] {:response-type :page
                 :title "Login"
                 :body logon-body}
 [:get "failed-logon"] {:response-type :page
                        :title "Login"
                        :body failed-logon-body}
 [:get "logoff"] {:response-type :redirect
                   :action logoff-action!
                   :new-session logoff-new-session
                   :redirect-uri logoff-redirect-uri})

;; These return the clays form the sample.message module. Notice how
;; we use the ?which syntax from matchure to get the message ID. Also,
;; notice how that data is included in the resulting maps, as
;; :message-id. You'll see how that gets used in the sample.message
;; module.
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


;; End of file
