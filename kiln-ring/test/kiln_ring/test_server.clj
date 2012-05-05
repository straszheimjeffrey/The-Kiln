(ns kiln-ring.test-server
  (use clojure.test
       [kiln-ring server request]
       kiln.kiln))

(deftest test-default-on-error
  (let [k (new-kiln)
        store (atom nil)
        c (clay :name c
                :value :a
                :cleanup-success (reset! store :success)
                :cleanup-failure (reset! store :failure))]
    (is (= (fire k c) :a))
    (is (= :exception-thrown
           (try
             (#'kiln-ring.server/default-on-error (Exception. "fred") k)
             :no-exception-thrown
             (catch Exception e
               :exception-thrown))))
    (is (= @store :failure))))


(def store (atom nil))

(defclay nice-clay
  :value (?? request)
  :cleanup-success (swap! store conj :nice-success)
  :cleanup-failure (swap! store conj :nice-failure))

(defclay mean-clay
  :value (throw (Exception. "Boom"))
  :cleanup-success (swap! store conj :mean-success)
  :cleanup-failure (swap! store conj :mean-failure))

(defclay nice-and-mean
  :value [(?? nice-clay) (?? mean-clay)])
  

(deftest test-kiln-ring-handler
  (reset! store [])
  (is (= ((kiln-ring-handler nice-clay) :request)
         :request))
  (is (= :exception-thrown
         (try
           ((kiln-ring-handler mean-clay) :request)
           :no-exception
           (catch Exception e
             :exception-thrown))))
  (is (= :exception-thrown
         (try
           ((kiln-ring-handler nice-and-mean) :request)
           :no-exception
           (catch Exception e
             :exception-thrown))))
  (is (= @store [:nice-success :nice-failure])))

(defn inner-wrapper
  [handler]
  (fn [request]
    (handler (assoc request :fred :mary :joan :inner))))

(defn outer-wrapper
  [handler]
  (fn [request]
    (handler (assoc request :bob :sally :joan :outer))))

(defn my-on-error
  [exp kiln]
  (swap! store conj (.getMessage exp))
  (cleanup-kiln-failure kiln)
  :blowed-up)

(deftest test-apply-kiln-handler
  (apply-kiln-handler nice-clay
                      :middleware [inner-wrapper outer-wrapper])
  (is (= (handler {:a :request})
         {:a :request
          :fred :mary
          :bob :sally
          :joan :inner}))
  (reset! store [])
  (apply-kiln-handler nice-and-mean
                      :on-error my-on-error)
  (is (= (handler {:a :request})
         :blowed-up))
  (is (= @store ["Boom" :nice-failure])))
    


(comment

(run-tests)

)


;; End of file
