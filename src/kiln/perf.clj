(ns kiln.perf
  (use [kiln.kiln]))

;; Experimental code to test performance

(defclay do-not-much
  :args [a]
  :value a)

(defn run-it
  [count]
  (let [k (new-kiln)]
    (dotimes [i count]
      (fire k do-not-much i))
    (dotimes [i count]
      (fire k do-not-much i))))
      
      


;; End of file
