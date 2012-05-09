(ns kiln.kiln-test
  (use clojure.test)
  (use slingshot.slingshot)
  (use kiln.kiln))

(def ^{:macro true} ko @#'kiln.kiln/kiln-ops)

(deftest test-new-kiln
  (let [k (new-kiln)]
    (ko
     (#'kiln.kiln/put-item-in-kiln k :fred :first)
     (#'kiln.kiln/put-item-in-kiln k :mary :second))
    (ko
     (#'kiln.kiln/add-cleanup-to-kiln k :a-cleanup)
     (#'kiln.kiln/put-item-in-kiln k :fred :third)
     (is (= (#'kiln.kiln/get-item-from-kiln k :fred)
            :third)))
    (ko
     (#'kiln.kiln/add-cleanup-to-kiln k :another-cleanup))
    (is (= (#'kiln.kiln/get-item-from-kiln k :fred)
           :third))
    (is (= (#'kiln.kiln/get-item-from-kiln k :mary)
           :second))
    (is (= (#'kiln.kiln/get-item-from-kiln k :bleh)
           :kiln.kiln/kiln-item-not-found))
    (is (not (#'kiln.kiln/is-kiln-cleaning? k)))
    (ko
     (is (= (#'kiln.kiln/get-cleanups-from-kiln k)
            '(:another-cleanup :a-cleanup)))
     (is (#'kiln.kiln/is-kiln-cleaning? k)))
    (ko
     (is (nil? (#'kiln.kiln/get-cleanups-from-kiln k))))))

;; Some basic coals

(defcoal coal-1 "Some bit of coal, an int")
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
  :cleanup (reset! (?? coal-2) [])
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
    (is (= (fire k fred) 6) "value saved")
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
    (reset! store [])
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

(deftest test-cleanup-order
  (let [k (new-kiln)
        cleanup-order (atom [])
        cleaning! #(swap! cleanup-order conj %)
        a (coal)
        b (clay :value (inc (?? a)) :cleanup (cleaning! 'b))
        c (clay :value (inc (?? b)) :cleanup (cleaning! 'c))
        d (clay :value (+ (?? b) (?? c)) :cleanup (cleaning! 'd))]
    (stoke-coal k a 42)
    (fire k d)
    (is (= [43 44 (+ 43 44)] (map (partial fire k) [b c d])))
    (cleanup-kiln-success k)
    (is (= '[d c b] @cleanup-order)
        "cleanup should have happened in the reverse order of firing")))

(deftest test-cleanup-after-firing-error
  (let [k (new-kiln)
        cleaned? (atom 0)
        external-rs (clay :value 42 :cleanup (swap! cleaned? inc))
        internal-rs (clay :value (do (?? external-rs) (throw+ ::whatever))
                          :cleanup (swap! cleaned? - 50000))]
    (is (try+ (fire k internal-rs), false
              (catch (= % ::whatever) _
                (cleanup-kiln-failure k), true)))
    (is (= 1 @cleaned?)
        "cleaned external-rs once, but never internal-rs")))

(deftest test-repeated-cleanup
  (let [k (new-kiln)
        store (atom [])
        a (clay :name a
                :value :a
                :cleanup (swap! store conj :cleanup)
                :cleanup-success (swap! store conj :success)
                :cleanup-failure (swap! store conj :failure))]
    (fire k a)
    (cleanup-kiln-failure k)
    (is (= @store [:cleanup :failure]))
    (cleanup-kiln-success k)
    (is (= @store [:cleanup :failure])
        "No extra cleaning after first")))

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
        bob! (clay :kiln zxqy
                   :value (swap! (?? coal-2) conj zxqy))]
    (stoke-coal k coal-2 store)
    (fire k bob!)
    (is (= @store [k]))))

(declare loopy-clay embrace)
(defclay loopy-clay :value (?? loopy-clay))
(defclay deadly :value (?? embrace))
(defclay embrace :value (?? deadly))

(deftest test-loopy-clay
  (let [k (new-kiln)]
    (is (= :exception-thrown
           (try+
            (fire k loopy-clay)
            (catch [:type :kiln-loop] {:keys [clay kiln]}
              (is (= clay loopy-clay))
              (is (= kiln k))
              :exception-thrown))))
    (is (try+ (fire k deadly), false
              (catch [:type :kiln-loop] _ true))
        "detects mutual, as well as self-recursion")))

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
    (reset! store [])
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
    (reset! store [])
    (cleanup-kiln-success k)
    (is (= @store [[22 10 11]
                   [6 2 3]]))))

(deftest destructuring-argument-tests
  (let [k (new-kiln)
        store (atom [])
        aa (clay :name aa
                 :args [& args]
                 :value (reduce + args))
        bb (glaze :name bb
                  :args [& args]
                  :operation (do (swap! store conj [args ?args])
                                 (?next)))
        cc (glaze :name cc
                  :args [a {:keys [b c]}]
                  :operation (do (swap! store conj [a b c ?args])
                                 (?next)))
        dd (clay :name dd
                 :glaze [(bb a b 3)
                         (cc a {:b b :c 4})]
                 :args [{a :a b :b}]
                 :value (+ a b))
        ee (clay :name ee
                 :args [a & {:keys [ralph barry]}]
                 :value (list a ralph barry))]
    (is (= (fire k aa 1 2) 3))
    (is (= (fire k dd {:a 5 :b 6}) 11))
    (is (= (fire k ee :fred :ralph :a :barry :c) (list :fred :a :c)))
    (is (= @store
           [[(list 5 6 3) {'a 5 'b 6}]
            [5 6 4 {'a 5 'b 6}]]))))

(deftest calling-??-in-glaze
  (let [k (new-kiln)
        store (atom [])
        a (coal)
        b (coal)
        c (glaze :name c
                 :args [x]
                 :operation (do (swap! store conj (+ x (?? b)))
                                (?next)))
        d (clay :name d
                :args [q]
                :glaze [(c q)]
                :value :fish)
        e (clay :name e
                :glaze [(c (?? a))]
                :value :bob)]
    (stoke-coal k a 1)
    (stoke-coal k b 2)
    (fire k d 3)
    (fire k e)
    (is (= @store [5 3]))))

(deftest exceptions-unwrap-correctly
  (let [k (new-kiln)
        a (clay :name a
                :value (throw+ {:type :exc}))]
    (is (= :exception-thrown)
        (try+
         (fire k a)
         :exception-not-thrown
         (catch [:type :exc] _
           :exception-thrown)))
    (is (= :kiln.kiln/clay-had-error
           (#'kiln.kiln/get-item-from-kiln k (#'kiln.kiln/clay-id a nil))))))

(deftest test-unsafe-set-clay!!
  (let [k (new-kiln)
        store (atom [])
        a (clay :name a
                :value :fred
                :cleanup (swap! store conj :a))
        b (clay :name b
                :args [fred ethel]
                :value (+ fred ethel)
                :cleanup (swap! store conj :b))]
    (unsafe-set-clay!! k a :mary)
    (unsafe-set-clay!! k b 0 0 1)
    (unsafe-set-clay!! k b 1 1 4)
    (is (= (fire k a) :mary))
    (is (= (fire k b 0 0) 1))
    (is (= (fire k b 1 1) 4))
    (is (= (fire k b 10 20) 30))
    (cleanup-kiln-success k)
    (is (= @store [:b]))))

(comment

(run-tests)

)

;; End of file
