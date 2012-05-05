(ns
    ^{:doc "A module to manage logon and logoff"
      :author "Jeffrey Straszheim"}
  sample.logon-logoff
  (use sample.utils
   kiln-ring.request
       kiln.kiln
       slingshot.slingshot
       hiccup.core))

;; A very stupid and simple logon mechanism. A real app would do real
;; things.


;; These are utils useful elsewhere

(defclay logged-on?
  :value (-> session ?? :user))

(defclay current-user-name
  :value (-> session ?? :user :name))

(defclay admin-user?
  :value (-> session ?? :admin?))

(defglaze require-logged-on
  :operation (if (?? logged-on?)
               (?next)
               (throw+ {:type :forced-redirect
                        :uri (?? logon-uri)})))

(defglaze require-admin
  :operation (if (?? admin-user?)
               (?next)
               (throw+ {:type :error-page
                        :message "not allowed"})))


;; The business logic

(defclay logon-action!
  :value (let [{:keys [name pass]} (?? params)]
           {:success? (= name pass)
            :admin? (= name "admin")
            :name name}))

(declare logoff-new-session)
(defclay logon-new-session
  :value (let [logon-stuff (?? logon-action!)]
           (if (:success? logon-stuff)
             {:user {:name (:name logon-stuff)}
              :admin? (:admin? logon-stuff)}
             (?? logoff-new-session))))

(defclay logon-redirect-uri
  :value (let [logon-stuff (?? logon-action!)]
           (if (:success? logon-stuff)
             (?? list-messages-uri)
             (?? error-uri "failed-logon"))))

(defclay logon-body
  :value (html
          [:form {:action (str (?? uri-with-path "/logon"))
                  :method "post"}
           [:p "Username"]
           [:p [:input {:type "text"
                        :name "name"}]]
           [:p "Password"]
           [:p [:input {:type "password"
                        :name "pass"}]]
           [:p [:input {:type "submit"}]]]))

(defclay logoff-action!
  :value :none)

(defclay logoff-new-session
  :value (dissoc (?? session) :user :admin?))

(defclay logoff-redirect-uri
  :value (?? logon-uri))

;; End of file