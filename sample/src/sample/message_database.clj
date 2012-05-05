(ns
    ^{:doc "A stupid and simple message database"
      :author "Jeffrey Straszheim"}
  sample.message-database)

;; This is a very simple in-memory message database.

(defonce ^{:private true
           :doc "The message store"}
  store
  (ref {}))

(defn- new-key
  []
  (let [current-keys (-> @store keys set)]
    (loop [attempted-key (rand-int 100000)]
      (if (current-keys attempted-key)
        (recur (rand-int 100000))
        attempted-key))))

(defn put-message
  [name header content]
  (dosync
   (let [key (new-key)]
     (alter store assoc key {:key key
                             :owner name
                             :header header
                             :content content}))))

(defn get-message
  [key]
  (get @store key))

(defn edit-message
  [key new-header new-content]
  (dosync
   (let [current-message (get-message key)]
     (alter store assoc key (assoc current-message
                              :header new-header
                              :content new-content)))))

(defn get-message-list
  []
  (for [message (-> store deref vals)]
    (select-keys message :key :owner :header)))