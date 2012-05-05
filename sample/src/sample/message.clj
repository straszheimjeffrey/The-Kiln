(ns
    ^{:doc "Messaging Kiln"
      :author "Jeffrey Straszheim"}
  sample.message
  (use [sample logon-logoff utils]
       kiln.kiln
       kiln-ring.request
       slingshot.slingshot
       hiccup.core)
  (require [sample.message-database :as md]))


;; This is the web applications message interface. It uses the
;; sample.message-database module to do the actual work.


;; Some clays about the current message.


;; This clay gets the current message id from the current
;; request. Actually, we get it from the dispatcher, as we took it out
;; using Matchure. This requires we break a circularity between
;; modules, which is ugly, but that's life.
(defclay message-id
  :value (-> (ns-resolve *ns* 'sample.dispatch/main-dispatch) ; circular includes
             deref
             ??
             :message-id))

(defclay current-message
  :value (md/get-message (?? message-id)))

(defclay current-message-owner
  :value (:owner (?? current-message)))

(defclay my-message?
  "Does the current user own this message. Note that admin owns *all*
messages."
  :value (or (?? admin-user?)
             (= (?? current-message-owner)
                (?? current-user-name))))


;; A glaze to require the current message belongs to the current
;; user. Or admin.
(defglaze require-my-message
  :operation (if (?? my-message?)
               (?next)
               (throw+ {:type :error-page
                        :message "Wrong User"})))


;; Here are the working clays:


;; The HTML for the main message list.
(defclay list-messages-body
  :glaze [require-logged-on]
  :value (let [ml (md/get-message-list)]
           (html
            (if (seq ml)
              [:ul.messages
               (for [{:keys [key owner header]} ml]
                 [:li
                  [:span.header (h header)]
                  [:br]
                  [:span.owner
                   "by: " (h owner)
                   [:br]
                   [:a {:href (->
                               (?? uri-with-path (format "/show-message/%s" key))
                               str)}
                    "(show message)"]]])]
              [:p.message "Sorry, no messages found"])
            [:p [:a {:href (str (?? uri-with-path "/new-message"))}
                 "(new message)"]])))

;; The HTML to show the current message.
(defclay show-message-body
  :glaze [require-logged-on]
  :value (let [{:keys [key owner header content]} (?? current-message)]
           [:ul
            [:li.header (h header)]
            [:li.owner "by " (h owner)]
            [:li.body (h content)]
            (when (?? my-message?)
              [:li [:a {:href (->
                               (?? uri-with-path
                                   (format "/edit-message/%s" key))
                               str)}
                    "(edit message)"]])]))

;; A form for adding a new message.
(defclay new-message-body
  :glaze [require-logged-on]
  :value (html
          [:form {:action (str (?? uri-with-path "/new-message"))
                  :method "post"}
           [:p "Header"]
           [:p [:input {:type "text" :name "header"}]]
           [:p "Body"]
           [:p [:textarea {:name "body"}]]
           [:p [:input {:type "submit"}]]]))

;; A form to edit the current message. Notice we have added the
;; require-my-message clay. You can only edit your own.
(defclay edit-message-body
  :glaze [require-logged-on
          require-my-message]
  :value (let [{:keys [key owner header content]} (?? current-message)]
           [:form {:method "post"}
            [:p "Header"]
            [:p [:input {:type "text" :name "header" :value header}]]
            [:p "Body"]
            [:p [:textarea {:name "body"}
                 (h content)]]
            [:p [:input {:type "submit"}]]]))

;; These next two post a new message and redirect.
(defclay new-message-action!
  :glaze [require-logged-on]
  :value (let [{:keys [body header]} (?? params)
               current-user-name (?? current-user-name)]
           (md/put-message current-user-name header body)))

(defclay new-message-redirect-uri
  :value (?? list-messages-uri))

;; These two edit the current message and redirect. Again note the
;; require-my-message glaze.
(defclay edit-message-action!
  :glaze [require-logged-on
          require-my-message]
  :value (let [{:keys [body header]} (?? params)
               current-user-name (?? current-user-name)]
           (md/edit-message (?? message-id) header body)))

(defclay edit-message-redirect-uri
  :value (?? uri-with-path (format "/show-message/%s" (?? message-id))))

;; End of file
