(ns
    ^{:doc "A new evuation strategy for complex computations."
      :author "Jeffrey Straszheim"}
  kiln.kiln
  (use slingshot.slingshot)
  (require [clojure.walk :as walk]))

(comment

  ;; A kiln

  {:vals (ref {})
   :needs-cleanup (ref [])}

  ;; A clay

  {:id 'some-id
   :name 'some-symbolic-name
   :fun ... ; executable
   :cleanup ... ; or...
   :cleanup-success ...
   :cleanup-failure ...
   :pre-compute [...]
   :extra-data { }
   }
  )

(defn new-kiln
  "Return a blank kiln ready to stoke and fire."
  []
  {::kiln? true
   :vals (ref {})
   :needs-cleanup (ref [])})

(defn- exec-in-env
  [kiln fun]
  (fun kiln))

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
         (do (when-let [pres (:pre-compute clay)]
               (doseq [pre pres] (fire kiln pre)))
             (dosync (alter (:vals kiln) assoc (:id clay) ::running))
             (let [result (exec-in-env kiln (:fun clay))]
               (dosync
                (alter (:vals kiln) assoc (:id clay) result)
                (when (has-cleanup? clay)
                  (alter (:needs-cleanup kiln) conj clay)))
               result))
         (throw+ {:type :kiln-absent-coal :coal clay :kiln kiln}))
       (throw+ {:type :kiln-uncomputed-during-cleanup :clay clay :kiln kiln})))))

(defn- cleanup
  [kiln key]
  (let [kiln (assoc kiln ::cleanup? true)]
    (doseq [clay @(:needs-cleanup kiln)]
      (when-let [fun (get clay key)]
        (fun kiln)))))
      
(defn cleanup-kiln-success
  "Run the cleanup and cleanup-success routines for each needed clay."
  [kiln]
  (cleanup kiln :cleanup)
  (cleanup kiln :cleanup-success))

(defn cleanup-kiln-failure
  "Run the cleanup and cleanup-failure routines for each needed clay."
  [kiln]
  (cleanup kiln :cleanup)
  (cleanup kiln :cleanup-failure))

;; Building Clays

(defn- get-pairs
  [stuff]
  (loop [stuff stuff
         data {}]
    (cond
     (nil? stuff)
     [nil data]

     (keyword? (first stuff))
     (recur (drop 2 stuff)
            (assoc data (first stuff) (second stuff)))

     :otherwise
     [stuff data])))

(defn- build-env-fun
  [form]
  (let [clay-sym (gensym "env")
        replace-fire (fn [f]
                       (if (and (seq? f)
                                (= (first f) '??))
                         (list 'fire clay-sym (second f))
                         f))]
    (list* 'fn
           [clay-sym]
           (walk/prewalk replace-fire form))))

(defmacro clay
  "Builds a clay object"
  [& clay]
  (let [[rest data-map] (get-pairs clay)
        build-if-present (fn [data key]
                           (if-let [form (get data key)]
                             (assoc data key (build-env-fun form))
                             data))
        set-symb (fn [data key val]
                   (let [symb (or (get data key) val)]
                     (assoc data key (list 'quote symb))))
        id (or (:id clay) (gensym "clay-"))]
    (-> data-map
        (set-symb :id id)
        (set-symb :name id)
        (assoc ::clay? true)
        (assoc :fun (build-env-fun rest))
        (build-if-present :cleanup)
        (build-if-present :cleanup-success)
        (build-if-present :cleanup-failure))))

(defmacro defclay
  "Define a clay at the top level, ensure the name is set correctly."
  [name & stuff]
  (let [[comment body] (if (string? (first stuff))
                         [(first stuff) (rest stuff)]
                         [nil stuff])]
    `(def ~(with-meta name {:doc comment})
       (clay :name (quote ~name) ~@stuff))))

(defmacro defcoal
  "Define a coal (source clay) at top level."
  [name & comment]
  (let [comment (if (string? (first comment)) (first comment) nil)]
    `(def ~(with-meta name {:doc comment})
       {:id (quote ~(gensym "coal-"))
        :name (quote ~name)
        ::coal? true})))

(comment

(def k (new-kiln))
(defcoal fred)
(defclay mary (+ (?? fred) 5))
(defclay joan (+ (?? mary) 2))

(stoke-coal k fred 3)
(prn (fire k mary))
(prn (fire k joan))
    
)

;; End of file
