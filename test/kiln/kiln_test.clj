(ns kiln.kiln-test
  (use clojure.test)
  (use slingshot.slingshot)
  (use kiln.kiln))

(deftest test-new-kiln
  (let [k (new-kiln)]
    (is (:kiln.kiln/kiln? k))
    (dosync (alter (:vals k) assoc :fred :mary))
    (dosync (alter (:needs-cleanup k) conj :fred))
    (is (= @(:vals k) {:fred :mary}))
    (is (= @(:needs-cleanup k) [:fred]))))

;; Some basic coals

(defcoal coal-1 "Some bit of coal, and int")
(defcoal coal-2 "Another bit of coal, an atom holding a vec")

(defclay fred
  "coal-1 plus 5"
  :value (+ (?? coal-1) 5))

(defclay mary!
  "Appends fred to coal-2"
  :value (swap! (?? coal-2) conj (?? fred)))

(defclay bob
  "coal-1 plus 2"
  :value (+ (?? coal-1) 2))

(defclay sally!
  "Double coal-2"
  :cleanup (swap! (?? coal-2) (constantly []))
  :value (do (?? bob)
             (swap! (?? coal-2) (fn [k] (vec (concat k k))))))
  
;; A very basic test

(deftest basic-kiln-test
  (let [k (new-kiln)
        store (atom [])]
    (stoke-coal k coal-1 1)
    (stoke-coal k coal-2 store)
    (is (not (clay-fired? k fred)))
    (is (= (fire k fred) 6))
    (fire k mary!)
    (is (= @store [6]))
    (fire k sally!)
    (is (= @store [6 6]))
    (fire k sally!)
    (is (= @store [6 6])) ; idempotent
    (is (clay-fired? k bob))
    (cleanup-kiln-success k)
    (is (= @store []))))

(deftest test-cleanups
  (let [k1 (new-kiln)
        k2 (new-kiln)
        store (atom [])
        one (clay :cleanup (swap! (?? coal-2) conj 1)
                  :value :fred)
        two (clay :cleanup-success (swap! (?? coal-2) conj 2)
                  :value :fred)
        three (clay :cleanup-failure (swap! (?? coal-2) conj 3)
                    :value :fred)]
    (stoke-coal k1 coal-2 store)
    (fire k1 one)
    (fire k1 two)
    (fire k1 three)
    (cleanup-kiln-success k1)
    (is (= @store [2 1]))
    (swap! store (constantly []))
    (stoke-coal k2 coal-2 store)
    (fire k2 one)
    (fire k2 two)
    (fire k2 three)
    (cleanup-kiln-failure k2)
    (is (= @store [3 1]))))

(deftest test-cleanup-self
  (let [k (new-kiln)
        store (atom [])
        fred (clay :value 55
                   :cleanup (swap! (?? coal-2) conj (+ ?self 10)))]
    (stoke-coal k coal-2 store)
    (fire k fred)
    (cleanup-kiln-success k)
    (is (= @store [65]))))

(deftest test-cleanup-exceptions
  (let [k (new-kiln)
        store (atom [])
        a (coal)
        b (clay :value (+ (?? a) 5)
                :cleanup (throw+ {:type :hi! :value :bob}))
        c (clay :value (+ (?? b) 5)
                :cleanup (throw+ {:type :hi! :value :mary}))]
    (stoke-coal k a 0)
    (fire k c)
    (is (= [:mary :bob]
           (try+
            (cleanup-kiln-success k)
            (catch [:type :kiln-cleanup-exception] {:keys [exceptions]}
              (map (fn [e] (-> e .getData :object :value)) exceptions)))))))

(defcoal qqq)
(defclay yyy)

(deftest test-id-persistent
  (let [qqq-id (:id qqq)
        yyy-id (:id yyy)]
    (defcoal qqq)
    (defclay yyy)
    (is (= qqq-id (:id qqq)))
    (is (= yyy-id (:id yyy)))))

(deftest test-anaphoric-kiln
  (let [k (new-kiln)
        store (atom [])
        bob! (clay :kiln qqq
                   :value (swap! (?? coal-2) conj qqq))]
    (stoke-coal k coal-2 store)
    (fire k bob!)
    (is (= @store [k]))))

(declare loopy-clay)
(defclay loopy-clay :value (?? loopy-clay))
  
(deftest test-loopy-clay
  (let [k (new-kiln)]
    (is (= :exception-thrown
           (try+
            (fire k loopy-clay)
            (catch [:type :kiln-loop] {:keys [clay kiln]}
              (is (= clay loopy-clay))
              (is (= kiln k))
              :exception-thrown))))))

(deftest basic-glaze-test
  (let [k (new-kiln)
        store (atom [])
        a (coal)
        b (glaze :name b
                 :operation (do
                              (swap! store conj ?clay)
                              (?next)))
        c (glaze :kiln fred
                 :name c
                 :operation (do
                              (swap! store conj fred)
                              (?next)))
        d (clay :glaze [b c]
                :value (+ (?? a) 1))
        e (glaze :name e
                 :operation :override)
        f (clay :glaze [b e c]
                :value :never-happen)]
    (stoke-coal k a 0)
    (is (= (fire k d) 1))
    (is (= @store [d k]))
    (is (= (fire k f) :override))
    (is (= @store [d k f]))))


(deftest basic-arguments-test
  (let [k (new-kiln)
        store (atom [])
        a (coal)
        b (glaze :name b
                 :operation (do
                              (swap! store conj ?args)
                              (?next)))
        c (clay :name c
                :args [fred mary]
                :glaze [b]
                :cleanup (do (swap! store conj ?self)
                             (swap! store conj fred)
                             (swap! store conj mary))
                :value (+ (?? a) fred mary))]
    (stoke-coal k a 1)
    (is (= (fire k c 2 3) 6))
    (is (= (fire k c 10 11) 22))
    (is (= @store [{'fred 2 'mary 3}
                   {'fred 10 'mary 11}]))
    (swap! store (constantly []))
    (cleanup-kiln-success k)
    (is (= @store [22 10 11 6 2 3]))))

(deftest advanced-arguments-tests
  (let [k (new-kiln)
        store (atom [])
        a (coal)
        b (glaze :name b
                 :args [x y]
                 :operation (do
                              (swap! store conj [x y ?args])
                              (?next)))
        c (glaze :name c
                 :args [z]
                 :operation (do
                              (swap! store conj [z ?args])
                              (?next)))
        d (clay :name d
                :args [aa bb]
                :glaze [(b aa 5)
                        (c bb)]
                :cleanup (swap! store conj [?self aa bb])
                :value (+ (?? a) aa bb))]
    (stoke-coal k a 1)
    (is (= (fire k d 2 3) 6))
    (is (= (fire k d 10 11) 22))
    (is (= @store [[2 5 {'aa 2 'bb 3}]
                   [3 {'aa 2 'bb 3}]
                   [10 5 {'aa 10 'bb 11}]
                   [11 {'aa 10 'bb 11}]]))
    (swap! store (constantly []))
    (cleanup-kiln-success k)
    (is (= @store [[22 10 11]
                   [6 2 3]]))))


(comment

(run-tests)

)

;; End of file
