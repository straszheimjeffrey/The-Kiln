(ns
    ^{:doc "The main dispatcher of the sample server"
      :author "Jeffrey Straszheim"}
  sample.dispatch
  (use [sample server request]
       kiln.kiln
       ring.util.response
       slingshot.slingshot))

(use 'clojure.pprint)
(defclay response-clay
  :value (do
           (pprint (?? request))
           (-> path-as-vector ?? pr-str response)))



(apply-kiln-handler response-clay)


;; End of file
