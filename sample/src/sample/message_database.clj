(ns
    ^{:doc "A stupid and simple message database"
      :author "Jeffrey Straszheim"}
  sample.message-database)

;; This is a very simple in-memory message database.

(defonce ^{:private true
           :doc "The message store"}
  store
  (ref {:messages []
        :index {}}))

(defn- clear-store [] (dosync (ref-set store {:messages []
                                              :index {}})))

(comment

(clear-store)

)

(defn- replace-by-key
  [messages message]
  (let [[front back] (split-with #(-> % :key (not= (:key message))) messages)]
    (vec (concat front [message] (rest back)))))

(defn- update-store
  [message]
  (dosync
   (alter store (fn [s]
                  (-> s
                      (update-in [:messages] replace-by-key message)
                      (update-in [:index] assoc (:key message) message))))))

(defn- new-key
  []
  (let [current-keys (-> @store :index keys set)]
    (loop [attempted-key (str (rand-int 100000))]
      (if (current-keys attempted-key)
        (recur (str (rand-int 100000)))
        attempted-key))))

(defn put-message
  [user-name header content]
  (dosync
   (let [key (new-key)
         new-message {:key key
                      :owner user-name
                      :header header
                      :content content}]
     (update-store new-message))))

(defn get-message
  [key]
  (-> store deref :index (get key)))

(defn edit-message
  [key new-header new-content]
  (dosync
   (let [current-message (get-message key)
         new-message (assoc current-message
                       :header new-header
                       :content new-content)]
     (update-store new-message))))

(defn get-message-list
  []
  (for [message (-> store deref :messages)]
    (select-keys message [:key :owner :header])))