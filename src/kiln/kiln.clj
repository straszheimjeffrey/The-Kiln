(ns
    ^{:doc "A new evuation strategy for complex computations."
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

(defn stoke-coal
  "Within the kiln, set the coal to the desired value."
  [kiln coal val]
  {:pre [(::kiln? kiln)
         (::coal? coal)]}
  (dosync
   (alter (:vals kiln) assoc (:id coal) val)))

(defn- has-cleanup?
  [clay]
  (or (:cleanup clay)
      (:cleanup-success clay)
      (:cleanup-failure clay)))

(defn- run-clay
  [kiln clay]
  (try
    (dosync (alter (:vals kiln) assoc (:id clay) ::running))
    ((:fun clay) kiln)
    (finally
     (dosync (alter (:vals kiln) assoc (:id clay) nil)))))

(defn fire
  "Run the clay within the kiln to compute/retrieve its value."
  [kiln clay] ; the clay can be a coal
  {:pre [(::kiln? kiln)
         (or (::clay? clay) (::coal? clay))]}
  (let [value (get @(:vals kiln) (:id clay) ::value-of-clay-not-found)]
    (cond
     (= value ::running) ; uh-oh, we have a loop.
     (throw+ {:type :kiln-loop :clay clay :kiln kiln})

     (not= value ::value-of-clay-not-found) ; yay! it's there!
     value

     :otherwise ; gotta get it.
     (if-not (::cleanup? kiln)
       (if (::clay? clay)
         (do (when-let [pres (if-let [prf (:pre-fire clay)] (prf) nil)]
               (doseq [pre pres] (fire kiln pre)))
             (let [result (run-clay kiln clay)]
               (dosync
                (alter (:vals kiln) assoc (:id clay) result)
                (when (has-cleanup? clay)
                  (alter (:needs-cleanup kiln) conj clay)))
               result))
         (throw+ {:type :kiln-absent-coal :coal clay :kiln kiln}))
       (throw+ {:type :kiln-uncomputed-during-cleanup :clay clay :kiln kiln})))))

(defn clay-fired?
  "Has this clay been fired?"
  [kiln clay]
  {:pre [(::kiln? kiln)
         (or (::clay? clay) (::coal? clay))]}
  (contains? @(:vals kiln) (:id clay)))

(defn- cleanup
  [kiln key]
  (let [kiln (assoc kiln ::cleanup? true)]
    (doseq [clay @(:needs-cleanup kiln)]
      (when-let [fun (get clay key)]
        (try
          (fun kiln (fire kiln clay))
          (catch Exception e
            (dosync (alter (:cleanup-exceptions kiln) conj e))))))))
      
(defn- cleanup-kiln-which
  [kiln which]
  {:pre [(::kiln? kiln)]}
  (cleanup kiln :cleanup)
  (cleanup kiln which)
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
                         `(fire ~clay-sym ~(second f))
                         f))]
    `(fn
       ~(vec (list* clay-sym other-args))
       ~(walk/prewalk replace-fire form))))

(def ^:private allowed-clay-kws #{:id :name :value
                                 :pre-fire :kiln
                                 :cleanup :cleanup-success :cleanup-failure
                                 :extra})

(defmacro clay
  "Builds a clay object"
  [& clay]
  (let [data-map (apply hash-map clay)
        env-id (or (:kiln data-map) (gensym "env"))
        build-cleanup (fn [data key]
                        (if-let [form (get data key)]
                          (assoc data key (build-env-fun form
                                                         env-id
                                                         ['?self]))
                          data))
        wrap-if-present (fn [data key]
                          (if-let [form (get data key)]
                            (assoc data key `(fn [] ~form))
                            data))
        set-symb (fn [data key val]
                   (let [symb (or (get data key) val)]
                     (assoc data key (list 'quote symb))))
        id (or (:id data-map) (gensym "clay-"))]
    (when-let [bads (bad-keys data-map allowed-clay-kws)]
      (throw+ {:type :kiln-bad-key :keys bads :clay clay}))
    (-> data-map
        (set-symb :id id)
        (set-symb :name id)
        (assoc ::clay? true)
        (assoc :fun (build-env-fun (:value data-map) env-id nil))
        (dissoc :value)
        (wrap-if-present :pre-fire)
        (build-cleanup :cleanup)
        (build-cleanup :cleanup-success)
        (build-cleanup :cleanup-failure))))

(defmacro defclay
  "Define a clay at the top level, ensure the name is set correctly."
  [name & stuff]
  (let [[comment body] (if (string? (first stuff))
                         [(first stuff) (rest stuff)]
                         [nil stuff])
        cur-var (ns-resolve *ns* name)
        id (if cur-var (:id @cur-var) nil)]
    `(def ~(with-meta name {:doc comment})
       (clay :name ~name :id ~id ~@body))))

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

(defmacro defcoal
  "Define a coal (source clay) at top level."
  [name & comment]
  (let [comment (if (string? (first comment)) (first comment) nil)
        cur-var (ns-resolve *ns* name)
        id (if cur-var (:id @cur-var) nil)
        id (or id (gensym "coal-"))]
    `(def ~(with-meta name {:doc comment})
       (coal :name ~name :id ~id))))


(comment
    
)

;; End of file
