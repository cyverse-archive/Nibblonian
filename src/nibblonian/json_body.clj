(ns nibblonian.json-body
  (:use [clojure.java.io :only [reader]]
        [nibblonian.error-codes]
        [slingshot.slingshot :only [try+ throw+]])
  (:require [cheshire.core :as cheshire]
            [clojure.tools.logging :as log]))

(defn- json-body?
  [request]
  (let [content-type (:content-type request)]
    (not (empty? (re-find #"^application/json" content-type)))))

(defn- valid-method?
  [request]
  (cond
    (= (:request-method request) "post") true
    (= (:request-method request) :post)  true
    (= (:request-method request) "put")  true
    (= (:request-method request) :put)   true
    :else false))

(defn parse-json-body [handler]
  (fn [request]
    (cond
      (not (valid-method? request))
      (handler request)

      (not (contains? request :body))
      (handler request)

      (not (json-body? request))
      (handler request)

      :else
      (try+
        (let [body-map (cheshire/decode-stream (reader (:body request)) true)
              new-req  (assoc request :body body-map)]
          (handler new-req))
        (catch error? err
          (log/error (format-exception (:throwable &throw-context)))
          (err-resp "parse-json" (:object &throw-context)))
        (catch java.lang.Exception e
          (log/error (format-exception (:throwable &throw-context)))
          (err-resp "parse-json" (unchecked &throw-context)))))))
