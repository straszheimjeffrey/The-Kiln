(ns
    ^{:doc "Some utilities"
      :author "Jeffrey Straszheim"}
  sample.utils
  (use kiln.kiln
       kiln-ring.request
       clojure.tools.logging))

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

(defclay failed-logon-uri
  :value (?? uri-with-path "/failed-logon"))

(defclay logoff-uri
  :value (?? uri-with-path "/logoff"))

(defclay list-messages-uri
  :value (?? uri-with-path "/list-messages"))

(defn- log-helper
  [level message clay-name extra show-extra? console-also?]
  (let [out (format "%s %s%s %s" message clay-name
                    (if show-extra? ":" "")
                    (if show-extra? (extra) ""))]
    (log level out)
    (when console-also? (println out))))

(defglaze log-glaze
  :args [log-level & options]
  :operation (let [options (set options)
                   show-results? (:show-results? options)
                   show-args? (:show-args? options)
                   log-to-console? (:log-to-console? options)
                   clay-name (:name ?clay)]
               (log-helper log-level "Running" clay-name
                           #(pr-str ?args) show-args? log-to-console?)
               (log log-level (format "Running %s" clay-name))
               (let [result (?next)]
                 (log-helper log-level "Completed" clay-name
                             #(pr-str result) show-results? log-to-console?)
                 result)))

;; End of file
