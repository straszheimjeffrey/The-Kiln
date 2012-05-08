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

(defn run-test
  [which]
  (time (run-it 10000 which)))


;; End of file
