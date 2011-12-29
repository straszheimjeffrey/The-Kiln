(ns
    ^{:doc "A new evuation strategy for complex computations."
      :author "Jeffrey Straszheim"}
  kiln.core
  (require [clojure.walk :as walk]))

(comment

  ;; A kiln

  {:vals (ref {})
   :so-far (ref [])}

  ;; A clay

  {:id 'some-id
   :name 'some-symbolic-name
   :fun ... ; executable
   :cleanup ... ; or...
   :cleanup-success ...
   :cleanup-failure ...
   :extra-data { }
   }
  )

(defn- exec-in-env
  [kiln fun]
  :nothing-yet)

(defn fire
  "Run the clay within the kiln to compute/retrieve its value."
  [kiln clay]
  :nothing-yet)

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
    (prn rest)
    (prn (build-env-fun rest))
    (-> data-map
        (set-symb :id id)
        (set-symb :name id)
        (assoc :fun (build-env-fun rest))
        (build-if-present :cleanup)
        (build-if-present :cleanup-success)
        (build-if-present :cleanup-failure))))
    

;; End of file
