(ns
    ^{:doc "A new evaluation strategy for complex computations."
      :author "Jeffrey Straszheim"}
  kiln.kiln
  (use slingshot.slingshot)
  (require [clojure.walk :as walk]))



(defn new-kiln
  "Return a blank kiln ready to stoke and fire."
  []
  {::kiln? true
   :vals (ref {})
   :needs-cleanup (ref nil)
   :cleanup-exceptions (ref [])})

(defn- clay-key
  [clay args]
  {:id (:id clay)
   :args args})

(defn stoke-coal
  "Within the kiln, set the coal to the desired value."
  [kiln coal val]
  {:pre [(::kiln? kiln)
         (::coal? coal)]}
  (dosync
   (alter (:vals kiln) assoc (clay-key coal nil) val)))

(defn- has-cleanup?
  [clay]
  (or (:cleanup clay)
      (:cleanup-success clay)
      (:cleanup-failure clay)))

(defn- run-clay
  [kiln clay args]
  (if-let [glazes (:glaze clay)]
    ;; Run with glazes
    (let [args-map (into {} (map (fn [a b] [a b]) (:args clay) args))
          clay-fun (fn [] (apply (:fun clay) kiln args))
          apply-glaze (fn [next gl-map]
                        (let [gl (:item gl-map)
                              gl-args (:args gl-map)]
                          (assert (::glaze? gl))
                          (fn []
                            (apply (:operation gl)
                                   kiln
                                   clay
                                   next
                                   args-map
                                   gl-args))))
          glazes (reverse (apply glazes args))]
      ((reduce apply-glaze clay-fun glazes)))
    ;; Simple non-glazed run
    (apply (:fun clay) kiln args)))
  
(defn fire
  "Run the clay within the kiln to compute/retrieve its value."
  [kiln clay & args] ; the clay can be a coal
  {:pre [(::kiln? kiln)
         (or (::clay? clay) (::coal? clay))
         (= (count args) (count (:args clay)))]}
  (let [key (clay-key clay args)
        value (get @(:vals kiln) key ::value-of-clay-not-found)]
    (cond
     (= value ::running) ; uh-oh, we have a loop.
     (throw+ {:type :kiln-loop :clay clay :kiln kiln})

     (not= value ::value-of-clay-not-found) ; yay! it's there!
     value

     :otherwise ; gotta get it.
     (if-not (::cleanup? kiln)
       (if (::clay? clay)
         (do
           ;; compute and store result
           (let [result (try
                          (dosync (alter (:vals kiln) assoc key ::running))
                          (run-clay kiln clay args)
                          (finally (dosync (alter (:vals kiln) assoc key nil))))]
             (dosync
              (alter (:vals kiln) assoc key result)
              (when (has-cleanup? clay)
                (alter (:needs-cleanup kiln) conj {:clay clay
                                                   :args args})))
             result))
         (throw+ {:type :kiln-absent-coal :coal clay :kiln kiln}))
       (throw+ {:type :kiln-uncomputed-during-cleanup :clay clay :kiln kiln})))))

(defn clay-fired?
  "Has this clay been fired?"
  [kiln clay & args]
  {:pre [(::kiln? kiln)
         (or (::clay? clay) (::coal? clay))]}
  (contains? @(:vals kiln) (clay-key clay args)))

(defn- cleanup
  [kiln keys]
  (let [kiln (assoc kiln ::cleanup? true)]
    (doseq [clay-map @(:needs-cleanup kiln)]
      (let [clay (:clay clay-map)
            args (:args clay-map)
            funs (keep #(get clay %) keys)]
        (try
          (assert (apply clay-fired? kiln clay args))
          (let [result (apply fire kiln clay args)]
            (doseq [fun funs]
              (apply fun kiln result args)))
          (catch Exception e
            (dosync (alter (:cleanup-exceptions kiln) conj e))))))))
      
(defn- cleanup-kiln-which
  [kiln which]
  {:pre [(::kiln? kiln)]}
  (cleanup kiln [:cleanup which])
  (dosync (ref-set (:needs-cleanup kiln) []))
  (if-not (empty? @(:cleanup-exceptions kiln))
    (throw+ {:type :kiln-cleanup-exception
             :kiln kiln
             :exceptions @(:cleanup-exceptions kiln)})))

(defn cleanup-kiln-success
  "Run the cleanup and cleanup-success routines for each needed clay."
  [kiln]
  (cleanup-kiln-which kiln :cleanup-success))
                    
(defn cleanup-kiln-failure
  "Run the cleanup and cleanup-failure routines for each needed clay."
  [kiln]
  (cleanup-kiln-which kiln :cleanup-failure))

(defn clay-extra-data
  "Return the extra data stored in a clay."
  [clay]
  {:pre [(::clay? clay)]}
  (:extra clay))


;; Building Clays

(defn- bad-keys
  [map allowed-fn]
  (let [result (filter (comp not allowed-fn) (keys map))]
    (if (empty? result) nil result)))

(defn- build-env-fun
  [form clay-sym other-args]
  (let [clay-sym (or clay-sym (gensym "env"))
        replace-fire (fn [f]
                       (if (and (seq? f)
                                (= (first f) '??))
                         `(fire ~clay-sym ~@(rest f))
                         f))]
    `(fn
       ~(vec (list* clay-sym other-args))
       ~(walk/prewalk replace-fire form))))

(defn- wrap-glazes
  [glazes args]
  (let [make-item (fn [i]
                    (cond
                     (symbol? i) {:item i :args nil}
                     (seq i) {:item (first i) :args (vec (rest i))}
                     (::clay? i) {:item i :args nil}
                     :otherwise (throw+ {:type :kiln-bad-glaze :which i})))
        items (map make-item glazes)]
    `(fn ~args ~(vec items))))


(def ^:private allowed-clay-kws #{:id :name :value
                                 :kiln :glaze :args
                                 :cleanup :cleanup-success :cleanup-failure
                                 :extra})

(defmacro clay
  "Builds a clay object. The arguments are alternating key-values, like this:

 (clay :glaze [glaze-one glaze-two]
       :args [item]
       :value (get-from-database item))

The allowed parameters are:

:id - The underlying id to use, must be unique, optional

:name - A symbol for debugging use, defaults to :id

:value - the code to run to produce the value. Withing this code, any
form such as (?? a-clay optional-args...) will be converted to a
command to fire that clay in the current kiln.

:kiln - a symbol, if present, the calling kiln can be accessed withing
the :value computination using this

:glaze - a vector of glazes (see below)

:args - a vec of symbols, the arguments that can be passed to this
clay. The args are visible within the :value, the :cleanups, and
the :glaze list.

:cleanup, :cleanup-success, :cleanup-failure - code to run at cleanup
time. :cleanup always runs, followed by either :cleanup-success
or :cleanup-failure, depending on which cleanup method is used. Each
cleanup routing is passed an additional argument ?self, which is the
original value computed for this clay. Also, the (?? clay ...) syntax
works here.

:extra - a map of extra data with user specified meaning

The glazes can be in two formats, a bare glaze (usually as a
symbol). For glazes that require arguments, provide a list with the
glaze first and the arguments following, like this:

 (clay :glaze [(some-glaze-with-args 3 4)
               a-normal-glaze])"
  [& clay]
  (let [data-map (apply hash-map clay)
        env-id (or (:kiln data-map) (gensym "env"))
        args (or (:args data-map) [])
        build-cleanup (fn [data key]
                        (if-let [form (get data key)]
                          (assoc data key (build-env-fun form
                                                         env-id
                                                         (cons '?self args)))
                          data))
        data-map (if-let [glazes (:glaze data-map)]
                   (assoc data-map :glaze (wrap-glazes glazes args))
                   data-map)
        set-symb (fn [data key val]
                   (let [symb (or (get data key) val)]
                     (assoc data key (list 'quote symb))))
        id (or (:id data-map) (gensym "clay-"))]
    (when-let [bads (bad-keys data-map allowed-clay-kws)]
      (throw+ {:type :kiln-bad-key :keys bads :clay clay}))
    (-> data-map
        (assoc :args (list 'quote args))
        (set-symb :id id)
        (set-symb :name id)
        (assoc ::clay? true)
        (assoc :fun (build-env-fun (:value data-map) env-id args))
        (dissoc :value)
        (build-cleanup :cleanup)
        (build-cleanup :cleanup-success)
        (build-cleanup :cleanup-failure))))

(def ^:private allowed-coal-kws #{:id :name})

(defmacro coal
  "Build a coal."
  [& coal]
  (let [data-map (apply hash-map coal)
        id (or (:id data-map) (gensym "coal-"))]
    (when-let [bads (bad-keys data-map allowed-coal-kws)]
      (throw+ {:type :kiln-bad-key :keys bads :coal coal}))
    {::coal? true
     :id (list 'quote id)
     :name (list 'quote (or (:name data-map) id))}))

(def ^:private allowed-glaze-kws #{:id :name :kiln :operation :args})

(defmacro glaze
  "Build a glaze"
  [& glaze]
  (let [data-map (apply hash-map glaze)
        args (or (:args data-map) [])
        id (or (:id data-map) (gensym "glaze-"))
        name (or (:name data-map) id)
        env-id (or (:kiln data-map) (gensym "env"))]
    (when-let [bads (bad-keys data-map allowed-glaze-kws)]
      (throw+ {:type :kiln-bad-key :keys bads :glaze glaze}))
    {::glaze? true
     :args (list 'quote args)
     :id (list 'quote id)
     :name (list 'quote name)
     :operation (build-env-fun (:operation data-map)
                               env-id
                               (concat ['?clay '?next '?args] args))}))



;; top level

(defn- define-preserving-id
  [name builder stuff]
  (let [[comment body] (if (string? (first stuff))
                         [(first stuff) (rest stuff)]
                         [nil stuff])
        q-name (symbol (-> *ns* ns-name str) (str name))
        builder (symbol "kiln.kiln" builder)
        cur-var (resolve name)
        id (if cur-var (:id @cur-var) nil)]
    `(def ~(with-meta name (assoc (meta name) :doc comment))
       (~builder :name ~q-name :id ~id ~@body))))
    
(defmacro defclay
  "Define a clay at the top level, ensure the name is set correctly."
  [name & stuff]
  (define-preserving-id name "clay" stuff))

(defmacro defcoal
  "Define a coal (source clay) at top level."
  [name & stuff]
  (define-preserving-id name "coal" stuff))

(defmacro defglaze
  "Define a glaze at the top level"
  [name & stuff]
  (define-preserving-id name "glaze" stuff))

(comment
    
)

;; End of file
