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

(defn unsafe-set-clay!!
  "This will set the value of a clay within the kiln without
evaluating it. It is intended mostly for testing (which means it will
be abused). Note the value is the last argument. Any others between
clay and the last are considered the clay's arguments."
  [kiln clay & args-and-val]
  {:pre [(::kiln? kiln)
         (::clay? clay)
         (= (count args-and-val) (+ 1 (count (:args clay))))]}
  (let [val (last args-and-val)
        args (butlast args-and-val)]
    (dosync
     (alter (:vals kiln) assoc (clay-key clay args) val))))

(defn- has-cleanup?  [clay]
  (or (:cleanup clay)
      (:cleanup-success clay)
      (:cleanup-failure clay)))
  
(defn fire
  "Run the clay within the kiln to compute/retrieve its value."
  [kiln clay & args] ; the clay can be a coal
  {:pre [(::kiln? kiln)
         (or (::clay? clay) (::coal? clay))
         (= (count args) (count (:args clay)))]}
  (when-not (:transaction-allowed? clay)
    (io! "Non-transactional clay evaluated within transaction."))
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
                          (apply (:fun clay) kiln clay args)
                          (catch Throwable e
                            (dosync (alter (:vals kiln) assoc key nil))
                            (throw e)))]
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
  "Run the cleanup and cleanup-success routines for each needed
  clay. Cleanups are run in reverse order from when the clay was
  invoked."
  [kiln]
  (cleanup-kiln-which kiln :cleanup-success))
                    
(defn cleanup-kiln-failure
  "Run the cleanup and cleanup-failure routines for each needed
  clay. Cleanups are run in reverse order from when the clay was
  invoked."
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

(defn- wrap-fires
  [kiln-sym form]
  `(letfn [(~'?? [clay# & args#] (apply fire ~kiln-sym clay# args#))]
     ~form))

(defn- build-env-fun
  [form kiln-sym other-args]
  `(fn
     ~(vec (list* kiln-sym other-args))
     ~(wrap-fires kiln-sym form)))

(defn- wrap-glazes
  [glazes value kiln-sym args]
  (let [clay-sym (gensym "clay")
        args-sym (gensym "args")
        glaze-item
        (fn [glaze-reference next-sym]
          (let [[glaze-symb glaze-args]
                (cond
                 (symbol? glaze-reference) [glaze-reference nil]
                 (seq glaze-reference) [(first glaze-reference)
                                        (rest glaze-reference)])]
            `((:operation ~glaze-symb)
              ~kiln-sym
              ~clay-sym
              ~next-sym
              ~args-sym
              ~@glaze-args)))
        combine
        (fn [inner-form glaze-form]
          (let [next-sym (gensym "next")]
            `(let [~next-sym (fn [] ~inner-form)]
               ~(glaze-item glaze-form next-sym))))
        wrap-if-glazes-present
        (fn [form]
          (if (empty? glazes)
            form
            `(let [~args-sym (apply hash-map (interleave '~args
                                                         (list ~@args)))]
               ~form)))]
    (->
     (reduce combine value glazes)
     wrap-if-glazes-present
     (build-env-fun kiln-sym (list* clay-sym args)))))
  
(def ^:private allowed-clay-kws #{:id :name :value
                                  :kiln :glaze :args :transaction-allowed?
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

:value - the code to run to produce the value. Within this code, any
form such as (?? a-clay optional-args...) will be converted to a
command to fire that clay in the current kiln.

:kiln - a symbol, if present, the calling kiln can be accessed using
this symbol.

:glaze - a vector of glazes (see below)

:args - a vec of symbols, the arguments that can be passed to this
clay. The args are visible within the :value, the :cleanups, and
the :glaze list.

:transaction-allowed? - if true, this clay can be evaluated within a
dosync block, otherwise will fail as in core/io!.

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

 (clay :args [an-arg another-arg]
       :glaze [(some-glaze-with-args an-arg (?? some-clay))
               a-normal-glaze])
       :value (+ an-arg (?? a-different-clay another-arg)))"
  [& clay]
  (let [data-map (apply hash-map clay)
        kiln-sym (or (:kiln data-map) (gensym "kiln"))
        args (or (:args data-map) [])
        build-cleanup (fn [data key]
                        (if-let [form (get data key)]
                          (assoc data key (build-env-fun form
                                                         kiln-sym
                                                         (cons '?self args)))
                          data))
        fun (wrap-glazes (-> data-map :glaze reverse)
                         (:value data-map)
                         kiln-sym
                         args)
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
        (assoc :fun fun)
        (dissoc :value)
        (dissoc :glaze)
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
  "Build a glaze. See the docstring for clay for the basic principles. Glazes allow the following to be defined:

:id - as clay

:name - as clay

:kiln - as clay

:args - as clay

:operation - This stands in place of the clay :value. Inside, the

 (?? some-clay)

syntax works, and the arguments are available. Also, the following
symbols are defined:

?next - a zero argument function. This is what you must call to
compute the value of the surrounded clay. If you do not call it, the
clay will not be evaluated. Note: glazes form a chain. Calling

 (?next)

will actually evaluate the next glaze within this one.  When all
glazes are computed, the clay itself is.

?clay The wrapped clay

?args - A map of name-value pairs. These are the args that the *clay*
was called with, which are seperate from the args that this glaze was
called with.
"
  [& glaze]
  (let [data-map (apply hash-map glaze)
        args (or (:args data-map) [])
        id (or (:id data-map) (gensym "glaze-"))
        name (or (:name data-map) id)
        kiln-sym (or (:kiln data-map) (gensym "kiln"))]
    (when-let [bads (bad-keys data-map allowed-glaze-kws)]
      (throw+ {:type :kiln-bad-key :keys bads :glaze glaze}))
    {::glaze? true
     :args (list 'quote args)
     :id (list 'quote id)
     :name (list 'quote name)
     :operation (build-env-fun (:operation data-map)
                               kiln-sym
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
