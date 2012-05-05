(ns
    ^{:doc "Some URI Utilities"
      :author "Jeffrey Straszheim"}
  uri-utils
  (import java.net.URI))

(defprotocol uri-underlying-value
  (java-uri [self]))

(defn java-uri-absolute?
  [java-uri]
  (.getScheme java-uri))

(defn java-uri-opaque?
  [java-uri]
  {:pre [(java-uri-absolute? java-uri)]}
  (if-let [ssp (.getRawSchemeSpecificPart java-uri)]
    (not= (first ssp) "/")))

(defn java-uri-hierarchical [java-uri]
  (or (not (java-uri-absolute? java-uri))
      (not (java-uri-opaque? java-uri))))
    
(deftype uri [java-uri]

  uri-underlying-value
  (java-uri [self] java-uri)

  clojure.lang.Associative
  (containsKey [self key]
    (if (.valAt self key) true false))
  (entryAt [self key]
    (if-let [result (.valAt self key)] [key result] nil))
  (assoc [self key value]
    (let [{:keys [scheme host port path query fragment
                  user-info authority ssp]}
          self
          port (or port -1)]
      (uri.
       (condp = key
         :scheme (URI. value ssp fragment)
         :host (URI. scheme user-info value port path query fragment)
         :port (URI. scheme user-info host value path query fragment)
         :path (URI. scheme user-info host port value query fragment)
         :query (URI. scheme authority path value fragment)
         :fragment (URI. scheme ssp value)
         :user-info (URI. scheme value host port path query fragment)
         :authority (URI. scheme value path query fragment)
         :ssp (URI. scheme value fragment)
         :scheme-specific-part (URI. scheme value fragment)))))
  (valAt [self key]
    (condp = key
      :scheme (.getScheme java-uri)
      :host (.getHost java-uri)
      :port (.getPort java-uri)
      :path (.getRawPath java-uri)
      :query (.getRawQuery java-uri)
      :fragment (.getRawFragment java-uri)
      :user-info (.getRawUserInfo java-uri)
      :authority (.getRawAuthority java-uri)
      :scheme-specific-part (.getRawSchemeSpecificPart java-uri)
      :ssp  (.getRawSchemeSpecificPart java-uri)
      nil))
  (valAt [self key not-found]
    (if-let [result (.valAt self key)] result not-found))
  (equiv [self other]
    (let [{:keys [scheme ssp fragment]} self
          {o-scheme :scheme o-ssp :ssp o-fragment :fragment} other]
      (and (= scheme o-scheme)
           (= ssp o-ssp)
           (= fragment o-fragment))))
  (toString [self]
    (.toString java-uri)))

(defn as-uri
 [obj]
 (cond
  (instance? uri obj) obj
  (instance? URI obj) (uri. obj)
  (string? obj) (uri. (URI. obj))))
    

;; End of file
