(ns kiln-ring.test-request
  (use clojure.test
       kiln.kiln
       [kiln-ring request uri-utils]))

(def sample-request
  {:ssl-client-cert nil,
   :remote-addr "127.0.0.1",
   :params {:a "fred" :b "mary"}
   :session {:name "bob"}
   :cookies {"blah" {:value "bleh"}}
   :scheme :http,
   :request-method :get,
   :query-string "bob=phil",
   :content-type nil,
   :uri "/fred/mary/a",
   :server-name "localhost",
   :headers
   {"accept-encoding" "gzip, deflate",
    "cache-control" "max-age=0",
    "user-agent"
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10.6; rv:10.0.2) Gecko/20100101 Firefox/10.0.2",
    "connection" "keep-alive",
    "accept-language" "en-us,en;q=0.5",
    "accept"
    "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
    "host" "localhost:4448"},
   :content-length nil,
   :server-port 4448,
   :character-encoding nil,
   :body :not-set})

(deftest test-request
  (let [k (new-kiln)]
    (stoke-coal k request sample-request)
    (is (= (fire k request-uri)
           (as-uri "http://localhost:4448/fred/mary/a?bob=phil")))
    (is (= (fire k remote-addr)
           "127.0.0.1"))
    (is (= (fire k scheme)
           :http))
    (is (= (-> (fire k headers) (get "host"))
           "localhost:4448"))
    (is (= (-> (fire k headers) (get "cache-control"))
           "max-age=0"))
    (is (= (fire k headers "connection")
           "keep-alive"))
    (is (= (fire k params)
           {:a "fred" :b "mary"}))
    (is (= (fire k cookies)
           {"blah" {:value "bleh"}}))
    (is (= (fire k session)
           {:name "bob"}))))

(comment

(run-tests)

)


;; End of file
