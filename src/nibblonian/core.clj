(ns nibblonian.core
  (:use compojure.core)
  (:use [ring.middleware
         params
         keyword-params
         nested-params
         multipart-params
         cookies
         session]
        [nibblonian.request-utils]
        [nibblonian.controllers]
        [nibblonian.error-codes])
  (:require [compojure.route :as route]
            [compojure.handler :as handler]
            [nibblonian.query-params :as qp]
            [nibblonian.json-body :as jb]
            [clojure.tools.logging :as log]
            [nibblonian.irods-actions :as irods-actions])
  (:import [org.irods.jargon.core.exception JargonRuntimeException JargonException]))

(init)

(defn- do-apply
  [func & args]
  (let [retval {:succeeded true :retval nil :exception nil}]
    (try
      (assoc retval :retval (apply func args))
      (catch java.lang.Exception e
        (assoc retval :exception e :succeeded false)))))

(defn retry
  "Re-attempts a request a configurable number of times if a request fails."
  [func & args]
  (loop [num-tries 0]
    (let [attempt (apply do-apply (concat [func] args))]
      (if (and (not (:succeeded attempt)) (< num-tries (max-retries)))
        (do (Thread/sleep (retry-sleep))
          (recur (+ num-tries 1)))
        (if (:succeeded attempt)
          (:retval attempt)
          (throw (:exception attempt)))))))

(defroutes nibblonian-routes
  (GET "/" [] "Welcome to Nibblonian!")
  
  (GET "/home" request
       (retry do-homedir request))
  
  (POST "/exists" request
        (retry do-exists request))
  
  (POST "/download" request
        (retry do-download request))
  
  (GET "/display-download" request
       (retry do-special-download request))
  
  (GET "/upload" request
       (retry do-upload request))
  
  (GET "/directory" request
       (retry do-directory request))
  
  (POST "/directory/create" request
        (retry do-create request))
  
  (POST "/directory/rename" request
        (retry do-rename request irods-actions/rename-directory))
  
  (POST "/file/rename" request
        (retry do-rename request irods-actions/rename-file))
  
  (POST "/directory/delete" request
        (retry do-delete request irods-actions/delete-dirs))
  
  (POST "/file/delete" request
        (retry do-delete request irods-actions/delete-files))
  
  (POST "/directory/move" request
        (retry do-move request irods-actions/move-directories))
  
  (POST "/file/move" request
        (retry do-move request irods-actions/move-files))
  
  (GET "/file/download" request
       (retry do-download request))
  
  (GET "/file/preview" request
       (retry do-preview request))
  
  (GET "/file/manifest" request
       (retry do-manifest request))
  
  (GET "/file/metadata" request
       (retry do-metadata-get request))
  
  (GET "/file/tree-urls" request
       (retry do-tree-get request))
  
  (GET "/directory/metadata" request
       (retry do-metadata-get request))
  
  (POST "/file/metadata" request
        (retry do-metadata-set request))
  
  (POST "/file/tree-urls" request
        (retry do-tree-set request))
  
  (POST "/directory/metadata" request
        (retry do-metadata-set request))
  
  (DELETE "/file/metadata" request
          (retry do-metadata-delete request))
  
  (DELETE "/directory/metadata" request
          (retry do-metadata-delete request))
  
  (route/not-found "Not Found!"))

(defn site-handler [routes]
  (-> routes
    jb/parse-json-body
    wrap-multipart-params
    wrap-keyword-params
    wrap-nested-params
    qp/wrap-query-params))

(def app
  (site-handler nibblonian-routes))
