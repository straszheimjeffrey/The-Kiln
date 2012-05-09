(ns
    ^{:doc "A Handler that uses a Kiln to run a Ring."
      :author "Jeffrey Straszheim"}
  kiln-ring.server
  (use kiln-ring.request
       kiln.kiln))

(defn- default-on-error
  [exc kiln]
  (cleanup-kiln-failure kiln)
  (throw exc))

(defn kiln-ring-handler
  "Returns Kiln-Ring handler. Set response-clay to some clay that will
return a response object appropriate to Ring. The optional on-error
fun will be called if any Throwable occurs. It is passed the exception
and the still live kiln. You can also pass an optional kiln-type,
which must be :atom or :ref. This controls what locking mechanism is
used. See the docstring for new-kiln in the main library for an
explanation."
  [response-clay & {:keys [on-error kiln-type]}]
  (let [on-error (or on-error default-on-error)]
    (fn [req]
      (let [kiln (new-kiln kiln-type)]
        (try
          (stoke-coal kiln request req)
          (fire kiln response-clay)
          (catch Throwable e
            (on-error e kiln))
          (finally
           (cleanup-kiln-success kiln)))))))

(declare ^:private inner-handler)

(defn apply-kiln-handler
  "Activates the Kiln-Ring connector. The response-clay should be a
clay which returns a valid Ring response. Three keyword arguments are
supported: on-error, kiln-type, and middleware. On error should be a
function. If any Throwable is thrown, it is called with the exception
and the still live kiln. The default handler calls
cleanup-kiln-failure and rethrows. The kiln-type parameter should
be :ref or :atom. It can be left out, and is described in the
docstring for new-kiln in the main library.  If middleware is
provided, it should be a vec, where each must be a Ring style wrap-*
type function. The middleware is applied so that the last in the vec
is the outermost wrapper."
  [response-clay & {:keys [on-error kiln-type middleware]}]
  (let [handler (kiln-ring-handler response-clay
                                   :on-error on-error
                                   :kiln-type kiln-type)]
    (alter-var-root
     #'inner-handler
     (fn [_]
       (reduce (fn [f n] (n f)) handler middleware)))))

(defn handler
  [request]
  (#'inner-handler request))

;; End of file
