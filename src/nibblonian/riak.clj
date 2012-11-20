(ns nibblonian.riak
  (:use nibblonian.config
        clojure-commons.error-codes
        [slingshot.slingshot :only [try+ throw+]])
  (:require [clj-http.client :as cl]
            [clojure.tools.logging :as log]
            [clojure.string :as string]
            [clojure-commons.file-utils :as ft]
            [cemerick.url :as curl]))

(defn- key-url
  [url-path]
  (str (str (curl/url (riak-base-url) url-path))
       "?returnbody=true"))

(defn- request-failed
  [resp]
  (throw+ {:error_codes ERR_REQUEST_FAILED
           :body (:body resp)}))

(defn get-tree-urls
  [url-path]
  (let [resp (cl/get (key-url url-path) {:throw-exceptions false})]
    (cond
     (<= 200 (:status resp) 299) (:body resp)
     (= 404 (:status resp))      "{\"tree-urls\" : []}"
     :else                       (request-failed resp))))

