(ns
    ^{:doc "A stupid and simple message database"
      :author "Jeffrey Straszheim"}
  sample.message-database)

;; This is a very simple in-memory message database.

;; I don't comment this because it is not Kiln related: very basic
;; Clojure.

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
  (let [current-keys (->> @store :messages (map :key) set)]
    (loop [attempted-key (str (rand-int 100000))]
      (if (current-keys attempted-key)
        (recur (str (rand-int 100000)))
        attempted-key))))

(defn put-message
  "Put a new message in the database."
  [user-name header content]
  (dosync
   (let [key (new-key)
         new-message {:key key
                      :owner user-name
                      :header header
                      :content content}]
     (update-store new-message))))

(defn get-message
  "Return a message from the database. It is a map
with :key, :owner, :header, and :content, all Strings."  
  [key]
  (-> store deref :index (get key)))

(defn edit-message
  "For the given key, update the message header and content. Does no
error checking."
  [key new-header new-content]
  (dosync
   (let [current-message (get-message key)
         new-message (assoc current-message
                       :header new-header
                       :content new-content)]
     (update-store new-message))))

(defn get-message-list
  "Return all the messages. A seq of map, with keys :key, :owner, and :header. The bodies have been remvoed."
  []
  (for [message (-> store deref :messages)]
    (select-keys message [:key :owner :header])))

;; End of file
