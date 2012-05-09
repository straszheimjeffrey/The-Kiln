(ns kiln.perf
  (use [kiln.kiln]))

;; Experimental code to test performance

(defclay do-not-much
  :args [a]
  :value a)

(defn run-it
  [count which]
  (let [k (new-kiln which)]
    (dotimes [i count]
      (fire k do-not-much i))
    (dotimes [i count]
      (fire k do-not-much i))))

(defn run-long-test
  [which]
  (println (format "Long test %s" which))
  (time (run-it 100000 which)))

(defn run-short-test
  [which]
  (println (format "Short test %s" which))
  (time (run-it 1000 which)))

(comment

(do
  (println)
  (run-long-test :ref)
  (run-long-test :atom)
  (run-short-test :ref)
  (run-short-test :atom))

)

;; End of file
