(ns
    ^{:doc "A module to manage logon and logoff"
      :author "Jeffrey Straszheim"}
  sample.logon-logoff
  (use kiln.kiln
       slingshot.slingshot
       hiccup.core)
  (require (sample [utils :as utils])
           (kiln-ring [request :as request])))

;; A very stupid and simple logon mechanism. A real app would do real
;; things.


;; Here we have some basic clays about the current user.

(defclay logged-on?
  :value (-> request/session ?? :user))

(defclay current-user-name
  :value (-> request/session ?? :user :name))

(defclay admin-user?
  :value (-> request/session ?? :admin?))


;; These are some glazes you can add to a clay to provide
;; security. You can see these get used in the sample.message module.

(defglaze require-logged-on
  :operation (if (?? logged-on?)
               (?next)
               (throw+ {:type :forced-redirect
                        :uri (?? utils/logon-uri)})))

(defglaze require-admin
  :operation (if (?? admin-user?)
               (?next)
               (throw+ {:type :error-page
                        :message "not allowed"})))


;; The business logic

;; Here we just make sure the name and password match: a very silly
;; way to logon. The result is a map, which is used below.
(defclay logon-action!
  :value (let [{:keys [name pass]} (?? request/params)]
           {:success? (and name
                           (> (count name) 0)
                           (= name pass))
            :admin? (= name "admin")
            :name name}))

;; When we logon, we need to update the user's session. If logon
;; failed, we clear any user data.
(declare logoff-new-session)
(defclay logon-new-session
  :value (let [logon-stuff (?? logon-action!)]
           (if (:success? logon-stuff)
             {:user {:name (:name logon-stuff)}
              :admin? (:admin? logon-stuff)}
             (?? logoff-new-session))))

;; As with the session, where we redirect is determined by your logon.
(defclay logon-redirect-uri
  :value (let [logon-stuff (?? logon-action!)]
           (if (:success? logon-stuff)
             (?? utils/list-messages-uri)
             (?? utils/failed-logon-uri))))


;; Here is the logon form:

;; Some HTML.
(defn- logon-body-text
  [uri error-message]
  (html
   [:h2.error (h error-message)]
   [:form {:action (str uri)
           :method "post"}
    [:p "Username"]
    [:p [:input {:type "text"
                 :name "name"}]]
    [:p "Password"]
    [:p [:input {:type "password"
                 :name "pass"}]]
    [:p [:input {:type "submit"}]]]))

;; Return the basic logon form.
(defclay logon-body
  :value (logon-body-text (?? utils/logon-uri) nil))

;; Same, but with an error message.
(defclay failed-logon-body
  :value (logon-body-text (?? utils/logon-uri)
                          "Sorry, we do not recognize that name and password"))


;; The logoff stuff is pretty obvious.

(defclay logoff-action!
  :value :none)

(defclay logoff-new-session
  :value (dissoc (?? request/session) :user :admin?))

(defclay logoff-redirect-uri
  :value (?? utils/logon-uri))

;; End of file