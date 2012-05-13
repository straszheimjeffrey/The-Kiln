(ns sample.test-response
  (use clojure.test
       kiln.kiln
       kiln-ring.uri-utils)
  (require (sample [response :as response]
                   [message-database :as md])
           (kiln-ring [request :as request]
                      [server :as server])))

(def sample-request
  {:ssl-client-cert nil
   :remote-addr "127.0.0.1"
   :scheme :http
   :query-params {}
   :session :blank
   :form-params {}
   :request-method :blank
   :query-string nil
   :content-type nil
   :cookies
   {"ring-session" {:value "easy-to-guess"}}
   :uri :blank
   :server-name "localhost"
   :params :blank
   :headers
   {"accept-encoding" "gzip deflate"
    "cache-control" "max-age=0"
    "user-agent" "Not-Mozilla (Frustrated Badger 1.3)"
    "connection" "keep-alive"
    "accept-language" "en-usen;q=0.5"
    "accept"
    "text/htmlapplication/xhtml+xmlapplication/xml;q=0.9*/*;q=0.8"
    "host" "localhost:4448"
    "cookie" "ring-session=easy-to-guess"}
   :content-length nil
   :server-port -1
   :character-encoding nil})

(defn chain-responses
  [stuff]
  (let [step (fn [session
                  {:keys [path params method tests]}]
               (let [kiln (new-kiln)
                     path (if (fn? path) (path) path)]
                 (stoke-coal kiln request/request (assoc sample-request
                                                    :uri path
                                                    :request-method method
                                                    :params params
                                                    :session session))
                 (let [response (fire kiln response/response-clay)]
                   (tests response)
                   (merge session (:session response)))))]
    (reduce step {} stuff)))

(defn get-location
  [response]
  (-> response :headers (get "Location") as-uri))

(deftest basic-flow
  (let [store (atom nil)]
    (#'sample.message-database/clear-store)
    (chain-responses
     [{:path "/"
       :method :get
       :params {}
       :tests #(is (-> % :headers (get "Location") as-uri :path (= "/logon")))}
      {:path "/logon"
       :method :get
       :params {}
       :tests (fn [{:keys [status body] :as res}]
                (is (= status 200))
                (is (re-matches #"(?ms).*<form action[^>]*/logon.*" body)))}
      {:path "/logon"
       :method :post
       :params {:name "fred" :pass "mary"}
       :tests #(is (-> % get-location :path (= "/failed-logon")))}
      {:path "/logon"
       :method :post
       :params {:name "fred" :pass "fred"}
       :tests #(is (-> % get-location :path (= "/list-messages")))}
      {:path "/list-messages"
       :method :get
       :params nil
       :tests (fn [{:keys [status body]}]
                (is (= status 200))
                (is (re-matches #"(?ms).*Sorry, no messages found.*" body))
                (is (re-matches #"(?ms).*href=[^>]*/new-message.*" body)))}
      {:path "/new-message"
       :method :get
       :params nil
       :tests (fn [{:keys [status body] :as req}]
                (is (= status 200))
                (is (re-matches #"(?ms).*<form action[^>]*/new-message.*" body)))}
      {:path "/new-message"
       :method :post
       :params {:header "header!" :body "body!"}
       :tests #(is (-> % get-location :path (= "/list-messages")))}
      {:path "/list-messages"
       :method :get
       :params nil
       :tests (fn [{:keys [body]}]
                (let [[[_ id]] (re-seq #"/show-message/(\d+)" body)]
                  (is id)
                  (reset! store id)))}
      {:path #(str "/show-message/" @store)
       :method :get
       :params nil
       :tests (fn [{:keys [body]}]
                (do
                  (is (re-matches #"(?ms).*class=\"header\">header!.*" body))
                  (is (re-matches #"(?ms).*class=\"body\">body!.*" body))))}
      {:path #(str "/edit-message/" @store)
       :method :get
       :params nil
       :tests (fn [{:keys [body]}]
                (do
                  (is (re-matches
                       #"(?ms).*input[^>]*name=\"header\"[^>]*value=\"header!\".*"
                       body))
                  (is (re-matches
                       #"(?ms).*<textarea.*>body!.*" body))))}
      {:path #(str "/edit-message/" @store)
       :method :post
       :params {:header "fred" :body "mary"}
       :tests #(is (-> % get-location :path (= (str "/show-message/" @store))))}
      {:path #(str "/show-message/" @store)
       :method :get
       :params nil
       :tests (do
                (fn [{:keys [body]}]
                  (is (re-matches #"(?ms).*class=\"header\">fred.*" body))
                  (is (re-matches #"(?ms).*class=\"body\">mary.*" body))))}

      ])))


(comment

(run-tests)

)


;; End of file