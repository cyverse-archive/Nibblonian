(ns nibblonian.riak
  (:use nibblonian.config
        clojure-commons.error-codes
        [slingshot.slingshot :only [try+ throw+]])
  (:require [clj-http.client :as cl]
            [clojure.tools.logging :as log]
            [clojure.string :as string]
            [clojure-commons.file-utils :as ft]))

(defn- key-url
  [key]
  (str (string/join "/" (map ft/rm-last-slash [(riak-base-url) (riak-trees-bucket) key]))
       "?returnbody=true"))

(defn- request-failed
  [resp]
  (throw+ {:error_codes ERR_REQUEST_FAILED
           :body (:body resp)}))

(defn get-tree-urls
  [key]
  (let [resp (cl/get (key-url key) {:throw-exceptions false})]
    (cond
     (<= 200 (:status resp) 299) (:body resp)
     (= 404 (:status resp))      "{}"
     :else                       (request-failed resp))))

(defn set-tree-urls
  [key new-body]
  (let [resp (cl/post (key-url key) {:content-type :json :body new-body} {:throw-exceptions false})]
    (cond
     (<= 200 (:status resp) 299) 200
     (= 404  (:status resp))     404
     :else                       (request-failed resp))))

