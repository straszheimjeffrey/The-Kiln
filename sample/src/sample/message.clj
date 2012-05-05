(ns
    ^{:doc "Messaging Kiln"
      :author "Jeffrey Straszheim"}
  sample.message
  (use [sample logon-logoff utils]
       kiln.kiln
       hiccup.core)
  (require [sample.message-database :as md]))


(defclay message-id
  :value (-> (ns-resolve *ns* 'sample.dispatch/main-dispatch) ; circular includes
             deref
             ??
             :message-id))

(defclay list-messages-body
  :glaze [require-logged-on]
  :value (let [ml (md/get-message-list)]
           (html
            (if (seq ml)
              [:ul.messages
               (for [{:keys [key owner header]} ml]
                 [:li
                  [:p.header (h header)]
                  [:p.owner (h owner)]])]
              [:p.message "Sorry, no messages found"]))))


(defclay show-message-body
  :glaze [require-logged-on]
  :value (let [{:keys [key owner header content]} (md/get-message (?? message-id))]
           [:table
            [:tr [:th.header (h header)]]
            [:tr [:td.owner (h owner)]]
            [:tr [:td.content (h content)]]]))
  

(defclay new-message-body
  )

(defclay edit-message-body
  )

(defclay new-message-action!
  )

(defclay new-message-redirect-uri
  )

(defclay edit-message-action!
  )

(defclay edit-message-redirect-uri
  )

;; End of file
