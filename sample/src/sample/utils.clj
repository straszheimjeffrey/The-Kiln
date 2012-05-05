(ns
    ^{:doc "Some utilities"
      :author "Jeffrey Straszheim"}
  sample.utils
  (use kiln.kiln
       kiln-ring.request))

;; Some URI's

(defclay uri-with-path
  :args [path]
  :value (-> (?? request-uri)
             (assoc :path path
                    :query nil)))
                    

(defclay root-uri
  :value (?? uri-with-path "/"))

(defclay logon-uri
  :value (?? uri-with-path "/logon"))

(defclay logoff-uri
  :value (?? uri-with-path "/logoff"))

(defclay list-messages-uri
  :value (?? uri-with-path "/list-messages"))

(defclay error-uri
  :args [error]
  :value (?? uri-with-path (format "/error/%s" error)))
                 

;; End of file
