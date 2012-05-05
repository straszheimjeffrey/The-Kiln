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


;; This is a nice little utility glaze. I do not provide it in the
;; main libraries, as it depends on clojure.tools.logger, and I like
;; to keep my library dependencies light. Feel free to steal it.

(defn- log-helper
  [level message clay-name extra show-extra? console-also?]
  (let [out (format "%s %s%s %s" message clay-name
                    (if show-extra? ":" "")
                    (if show-extra? (extra) ""))]
    (log level out)
    (when console-also? (println out))))

(defglaze log-glaze
  "Log the operation of this clay to clojure.tools.logging. Note when
it begins evaluation and when it ends.

log-level is :trace, :debug, :info, :warn, or :error, as per
clojure.tools.logging.

The options are bare keywords. They can be :show-results?, show-args?, and log-to-console?

show-results? adds a pr-str of the clay's result to the log.

show-args? adds a prn-str of the clay's args map.

log-to-console? also calls println on the message."
  :args [log-level & options]
  :operation (let [options (set options)
                   show-results? (:show-results? options)
                   show-args? (:show-args? options)
                   log-to-console? (:log-to-console? options)
                   clay-name (:name ?clay)]
               (log-helper log-level "Running" clay-name
                           #(pr-str ?args) show-args? log-to-console?)
               (let [result (?next)]
                 (log-helper log-level "Completed" clay-name
                             #(pr-str result) show-results? log-to-console?)
                 result)))

;; End of file
