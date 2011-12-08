(ns nibblonian.query-params
  (:require [ring.util.codec :as codec]
            [clojure.string :as string]))

(defn assoc-param
  "Taken from ring.middleware.params. Needed because we need to write
   a function that uses hidden functions. 

  Associate a key with a value. If the key already exists in the map,
  create a vector of values."
  [map key val]
  (assoc map key
         (if-let [cur (map key)]
           (if (vector? cur)
             (conj cur val)
             [cur val])
           val)))

(defn parse-params
  "Taken from ring.middleware.params. Needed because we need to write
   a function that uses hidden functions. 

   Parse parame+ters from a string into a map."
  [^String param-string encoding]
  (reduce
   (fn [param-map encoded-param]
     (if-let [[_ key val] (re-matches #"([^=]+)=(.*)" encoded-param)]
       (assoc-param param-map
                    (codec/url-decode key encoding)
                    (codec/url-decode (or val "") encoding))
       param-map))
   {}
   (string/split param-string #"&")))

(defn assoc-query-params
  "Taken from ring.middleware.params. Needed because we need to write
   a function that uses hidden functions. 

   Parse and assoc parameters from the query string with the request."
  [request encoding]
  (merge-with merge request
              (if-let [query-string (:query-string request)]
                (let [params (parse-params query-string encoding)]
                  {:query-params params, :params params})
                {:query-params {}, :params {}})))

(defn wrap-query-params
  "Middleware to parse parameters from the query string (if it exists). It
   does not touch the form body. Adds the following key to the request map.
     :query-params - a map of parameters from the query string
   Takes an optional configuration map. Recognized keys are:
     :encoding - encoding to use for url-decoding."
  [handler & [opts]]
  (fn [request]
    (let [encoding (or (:encoding opts)
                       (:character-encoding request)
                       "UTF-8")
          request (if (:query-params request)
                    request
                    (assoc-query-params request encoding))]
      (handler request))))