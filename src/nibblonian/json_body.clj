(ns nibblonian.json-body
  (:require [clojure.contrib.json :as json]))

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
      (let [body-string (slurp (:body request))
            body-map (json/read-json body-string)
            new-req  (assoc request :body body-map)]
        (handler new-req)))))

