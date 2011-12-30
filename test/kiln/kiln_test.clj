(ns kiln.kiln-test
  (use clojure.test)
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
  (+ (?? coal-1) 5))

(defclay mary!
  "Appends fred to coal-2"
  (swap! (?? coal-2) conj (?? fred)))

(defclay bob
  "coal-1 plus 2"
  (+ (?? coal-1) 2))

(defclay sally!
  "Double coal-2"
  :pre-compute [bob]
  :cleanup (swap! (?? coal-2) (constantly []))
  (swap! (?? coal-2) (fn [k] (vec (concat k k)))))
  
;; A very basic test

(deftest basic-kiln-test
  (let [k (new-kiln)
        store (atom [])]
    (stoke-coal k coal-1 1)
    (stoke-coal k coal-2 store)
    (is (= (fire k fred) 6))
    (fire k mary!)
    (is (= @store [6]))
    (fire k sally!)
    (is (= @store [6 6]))
    (is (= (get @(:vals k) (:id bob)) 3)) ; did sally! compute bob?
    (cleanup-kiln-success k)
    (is (= @store []))))

(deftest test-cleanups
  (let [k (new-kiln)
        store (atom [])
        one (clay :cleanup (swap! (?? coal-2) conj 1) (quote :fred))
        two (clay :cleanup-success (swap! (?? coal-2) conj 2) (quote :fred))
        three (clay :cleanup-failure (swap! (?? coal-2) conj 3) (quote :fred))]
    (stoke-coal k coal-2 store)
    (fire k one)
    (fire k two)
    (fire k three)
    (cleanup-kiln-success k)
    (is (= (set @store) #{1 2}))
    (cleanup-kiln-failure k)
    (is (= (set @store) #{1 2 3}))))

(defcoal qqq)
(defclay yyy)

(deftest test-id-persistant
  (let [qqq-id (:id qqq)
        yyy-id (:id yyy)]
    (defcoal qqq)
    (defclay yyy)
    (is (= qqq-id (:id qqq)))
    (is (= yyy-id (:id yyy)))))

  
(comment

(run-tests)

)

;; End of file
