(ns
    ^{:doc "A computational model for insanely complex functions."
      :author "Jeffrey Straszheim"}
  kiln.kiln
  (use slingshot.slingshot))

(defmacro ^:private kiln-error
  "Builds a nice slingshot error message."
  [type message & more]
  (let [symbs (-> &env keys set)
        more (concat more
                     (when (symbs 'clay)
                       `(:clay ~'clay))
                     (when (symbs 'coal)
                       `(:coal ~'coal))
                     (when (symbs 'glaze)
                       `(:glaze ~'glaze))
                     (when (symbs 'kiln)
                       `(:kiln ~'kiln)))]
    `(throw+ (hash-map :type ~type :message ~message ~@more))))

(defprotocol ^:private kiln-protocol
             (^:private get-item-from-kiln [self id])
             (^:private put-item-in-kiln [self id val])
             (^:private add-cleanup-to-kiln [self item])
             (^:private get-cleanups-from-kiln [self])
             (^:private is-kiln-cleaning? [self]))

(deftype ^:private ref-kiln
  [values cleanups]
  kiln-protocol
  (get-item-from-kiln [self id]
    (get @values id ::kiln-item-not-found))
  (put-item-in-kiln [self id val]
    (dosync (alter values assoc id val)))
  (add-cleanup-to-kiln [self item]
    (dosync (alter cleanups conj item)))
  (get-cleanups-from-kiln [self]
    (dosync (let [result @cleanups]
              (if (not= result ::currently-cleaning)
                (do (ref-set cleanups ::currently-cleaning)
                    result)
                nil))))
  (is-kiln-cleaning? [self]
    (= @cleanups ::currently-cleaning)))

(deftype ^:private atom-kiln
  [values cleanups]
  kiln-protocol
  (get-item-from-kiln [self id]
    (get @values id ::kiln-item-not-found))
  (put-item-in-kiln [self id val]
    (swap! values assoc id val))
  (add-cleanup-to-kiln [self item]
    (swap! cleanups conj item))
  (get-cleanups-from-kiln [self]
    (let [result @cleanups]
      (if (not= result ::currently-cleaning)
        (do (reset! cleanups ::currently-cleaning)
            result)
        nil)))
  (is-kiln-cleaning? [self]
    (= @cleanups ::currently-cleaning)))

(defn new-kiln
  "Return a blank kiln ready to stoke and fire. Can be passed a single
argument, kiln-type. The allowed values are :ref and :atom. Defaults
to ref. (The default may change in a future point release.) This
determines what locking mechanism the underlying kiln will use. Note
that kilns are *not* threadsafe, regardless of the chosen model. The
difference is in how the kiln behaves when fired *within* a dosync
block. A ref kiln will rollback. An atom kiln will not."
  [& [kiln-type]]
  (condp = (or kiln-type :ref)
    :ref (ref-kiln. (ref {}) (ref nil))
    :atom (atom-kiln. (atom {}) (atom nil))))

(defn- clay-id
  [clay args]
  [(:id clay) args])

(defn stoke-coal
  "Within the kiln, set the coal to the desired value."
  [^kiln.kiln.kiln_protocol kiln coal val]
  {:pre [(::coal? coal)]}
  (put-item-in-kiln kiln (clay-id coal nil) val))

(defn unsafe-set-clay!!
  "This will set the value of a clay within the kiln without
evaluating it. It is intended mostly for testing (which means it will
be abused). Note the value is the last argument. Any others between
clay and the last are considered the clay's arguments."
  [^kiln.kiln.kiln_protocol kiln clay & args-and-val]
  {:pre [(::clay? clay)]}
  (let [val (last args-and-val)
        args (butlast args-and-val)]
    (put-item-in-kiln kiln (clay-id clay args) val)))

(defn- has-cleanup?  [clay]
  (or (:cleanup clay)
      (:cleanup-success clay)
      (:cleanup-failure clay)))

(defn fire
  "Run the clay within the kiln to compute/retrieve its value."
  [^kiln.kiln.kiln_protocol kiln clay & args] ; the clay can be a coal
  {:pre [(or (::clay? clay) (::coal? clay))]}
  (let [id (clay-id clay args)
        value (get-item-from-kiln kiln id)]
    (cond
     (= value ::running) ; uh-oh, we have a loop.
     (kiln-error :kiln-loop "Cyclic Dependency in Kiln")

     (= value ::clay-had-error)
     (kiln-error :kiln-clay-had-error "Attempted to evaluate clay that already threw")

     (not= value ::kiln-item-not-found) ; yay! it's there!
     value

     :otherwise ; gotta get it.
     (do (when (is-kiln-cleaning? kiln)
           (kiln-error :kiln-uncomputed-during-cleanup
                       "Attempted to fire unevaluated clay during cleanup"))
         (when-not (::clay? clay)
           (kiln-error :kiln-absent-coal
                       "Attempted to fire unstoked coal"))
         ;; compute and store result
         (let [result (try
                        (put-item-in-kiln kiln id ::running)
                        ((:fun clay) kiln clay args)
                        (catch Throwable e
                          (put-item-in-kiln kiln id ::clay-had-error)
                          (throw e)))]
           (put-item-in-kiln kiln id result)
           (when (has-cleanup? clay)
             (add-cleanup-to-kiln kiln [clay args]))
           result)))))

(defn clay-fired?
  "Has this clay been fired?"
  [^kiln.kiln.kiln_protocol kiln clay & args]
  {:pre [(or (::clay? clay) (::coal? clay))]}
  (not= (get-item-from-kiln kiln (clay-id clay args))
        ::kiln-item-not-found))

(defn- cleanup
  [^kiln.kiln.kiln_protocol kiln keys]
  (let [exceptions (ref [])]
    (doseq [[clay args] (get-cleanups-from-kiln kiln)]
      (let [funs (keep #(get clay %) keys)]
        (assert (apply clay-fired? kiln clay args))
        (let [result (apply fire kiln clay args)]
          (doseq [fun funs]
            (try
              (apply fun kiln result args)
              (catch Exception e
                (dosync (alter exceptions conj e))))))))
    @exceptions))

(defn- cleanup-kiln-which
  [kiln which]
  (let [exceptions (cleanup kiln [:cleanup which])]
    (if-not (empty? exceptions)
      (kiln-error :kiln-cleanup-exception
                  "Nested exceptions threw in kiln cleanup"
                  :exceptions exceptions))))

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

(defn- all-symbols
  "Walks into a binding form and find everything that might be a var"
  [form]
  (->> form
       (tree-seq coll? seq)
       (filter symbol?)
       (remove #(= '& %))))

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
              ~kiln-sym ~clay-sym ~next-sym ~args-sym ~@glaze-args)))
        combine
        (fn [inner-form glaze-form]
          (let [next-sym (gensym "next")]
            `(let [~next-sym (fn [] ~inner-form)]
               ~(glaze-item glaze-form next-sym))))
        wrap-if-glazes-present
        (fn [form]
          (if (empty? glazes)
            form
            (let [arg-symbs (-> args all-symbols seq)]
              `(let [~args-sym (apply hash-map (interleave '[~@arg-symbs]
                                                           [~@arg-symbs] ))]
                 ~form))))
        wrap-with-args-binding
        (fn [form]
          `(let [~args ~args-sym]
             ~form))]
    (->
     (reduce combine value glazes)
     wrap-if-glazes-present
     wrap-with-args-binding
     (build-env-fun kiln-sym (list clay-sym args-sym)))))

(def ^:private allowed-clay-kws #{:id :name :value
                                  :kiln :glaze :args
                                  :cleanup :cleanup-success :cleanup-failure
                                  :extra})

(defmacro clay
  "Builds a clay object. The arguments are alternating key-values,
like this:

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

:cleanup, :cleanup-success, :cleanup-failure - code to run at cleanup
time. :cleanup always runs, followed by either :cleanup-success
or :cleanup-failure, depending on which cleanup method is used. Each
cleanup routine is passed an additional argument ?self, which is the
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
      (kiln-error :kiln-bad-key
                  (format "Illegal key in clay constructor: %s" (pr-str bads))
                  :keys bads))
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
      (kiln-error :kiln-bad-key
                  (format "Illegal key in coal construction: %s" (pr-str bads))
                  :keys bads))
    {::coal? true
     :id (list 'quote id)
     :name (list 'quote (or (:name data-map) id))}))

(def ^:private allowed-glaze-kws #{:id :name :kiln :operation :args})

(defmacro glaze
  "Build a glaze. See the docstring of clay for the basic
principles. Glazes allow the following to be defined:

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
      (kiln-error :kiln-bad-key
                  (format "Illegal key in glaze construction: %s" (pr-str bads))
                  :keys bads))
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


;; End of file
