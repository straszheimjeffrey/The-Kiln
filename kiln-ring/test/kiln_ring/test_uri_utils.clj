(ns kiln-ring.test-uri-utils
  (use clojure.test
       kiln-ring.uri-utils))

(def a-uri (as-uri "http://www.customer.com/a-page?some-query&&&#fred"))
(def with-a-port (as-uri "http://www.customer.com:999/?fred"))
(def bare (as-uri "fred"))
(def with-user-info (as-uri "http://fred@www.customer.com/"))

(defn- get-it-all
  [uri]
  [(:scheme uri)
   (:host uri)
   (:port uri)
   (:path uri)
   (:query uri)
   (:fragment uri)
   (:user-info uri)
   (:authority uri)
   (:scheme-specific-part uri)
   (:ssp uri)])

(deftest some-basic-tests
  (is (= (str a-uri)
         "http://www.customer.com/a-page?some-query&&&#fred"))
  (is (= (get-it-all a-uri)
         ["http"
          "www.customer.com"
          -1
          "/a-page"
          "some-query&&&"
          "fred"
          nil
          "www.customer.com"
          "//www.customer.com/a-page?some-query&&&"
          "//www.customer.com/a-page?some-query&&&"]))
  (is (= (get-it-all with-a-port)
         ["http"
          "www.customer.com"
          999
          "/"
          "fred"
          nil
          nil
          "www.customer.com:999"
          "//www.customer.com:999/?fred"
          "//www.customer.com:999/?fred"]))
  (is (= (get-it-all bare)
         [nil nil -1 "fred" nil nil nil nil "fred" "fred"]))
  (is (= (get-it-all with-user-info)
         ["http"
          "www.customer.com"
          -1
          "/"
          nil
          nil
          "fred"
          "fred@www.customer.com"
          "//fred@www.customer.com/"
          "//fred@www.customer.com/"])))

(deftest test-assoc
  (is (= (assoc a-uri :host "www.bobcat.com" :scheme "https")
         (as-uri "https://www.bobcat.com/a-page?some-query&&&#fred")))
  (is (= (assoc a-uri :port 99)
         (as-uri "http://www.customer.com:99/a-page?some-query&&&#fred"))))

(deftest test-as-java-uri
  (let [java-uri (java.net.URI. "http://www.customer.com/fred")]
    (is (= (as-java-uri (as-uri java-uri))
           java-uri))))

(deftest repeated-as-*
  (let [uri (as-uri "/fred")]
    (is (= uri (as-uri uri)))
    (is (= uri
           (-> uri
               as-java-uri
               as-java-uri
               as-uri
               str
               as-java-uri
               str
               as-uri)))))

(comment

(run-tests)

)

;; End of file
