(ns
    ^{:doc "The main dispatcher of the sample server"
      :author "Jeffrey Straszheim"}
  sample.dispatch
  (use [kiln-ring server request]
       kiln.kiln
       ring.util.response
       ring.util.servlet
       slingshot.slingshot)
  (:gen-class))

(use 'clojure.pprint)
(defclay response-clay
  :value (do
           (pprint (?? request))
           (-> (?? request-uri) str response)))

(apply-kiln-handler response-clay)

(servlet handler)

;; End of file
