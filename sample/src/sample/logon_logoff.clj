(ns
    ^{:doc "A module to manage logon and logoff"
      :author "Jeffrey Straszheim"}
  sample.logon-logoff
  (use kiln-ring.request
       kiln.kiln
       slingshot.slingshot
       hiccup.core))

;; A very stupid and simple logon mechanism. A real app would do real
;; things.

(defclay logon-action!
  :value (let [{:keys [name pass]} (?? params)]
           (prn name pass)
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
             (-> (?? request-uri)
                 (assoc :path "/"
                        :query nil))
             (-> (?? request-uri)
                 (assoc :path "/error/failed-logon"
                        :query nil)))))

(defclay logon-body
  :value (html
          [:form {:action (-> (?? request-uri)
                             (assoc :path "/logon"
                                    :query nil))
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
  :value (-> (?? request-uri)
             (assoc :path "/"
                    :query nil)))

;; End of file