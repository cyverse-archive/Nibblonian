(ns nibblonian.core
  (:gen-class)
  (:use compojure.core)
  (:use [ring.middleware
         params
         keyword-params
         nested-params
         multipart-params
         cookies
         session
         stacktrace]
        [nibblonian.request-utils]
        [nibblonian.controllers]
        [nibblonian.error-codes]
        [slingshot.slingshot :only [try+ throw+]])
  (:require [clojure.tools.cli :as cli] 
            [compojure.route :as route]
            [compojure.handler :as handler]
            [nibblonian.query-params :as qp]
            [nibblonian.json-body :as jb]
            [clojure.tools.logging :as log]
            [ring.adapter.jetty :as jetty]
            [nibblonian.irods-actions :as irods-actions])
  (:import [org.irods.jargon.core.exception JargonRuntimeException JargonException]))

(defroutes nibblonian-routes
  (GET "/" [] "Welcome to Nibblonian!")
  
  (GET "/home" request
       (trap "home" do-homedir request))
  
  (POST "/exists" request
        (trap "exists" do-exists request))
  
  (POST "/download" request
        (trap "download" do-download request))
  
  (GET "/display-download" request
       (trap "display-download" do-special-download request))
  
  (GET "/upload" request
       (trap "upload"  do-upload request))
  
  (GET "/directory" request
       (trap "list-dir" do-directory request))
  
  (POST "/directory/create" request
        (trap "create"  do-create request))
  
  (POST "/directory/rename" request
        (trap "rename-directory" 
               do-rename request irods-actions/rename-directory))
  
  (POST "/file/rename" request
        (trap "rename-file"  do-rename request irods-actions/rename-file))
  
  (POST "/directory/delete" request
        (trap "delete-dirs" 
               do-delete request irods-actions/delete-dirs))
  
  (POST "/file/delete" request
        (trap "delete-files"
               do-delete request irods-actions/delete-files))
  
  (POST "/directory/move" request
        (trap "move-dirs" 
               do-move request irods-actions/move-directories))
  
  (POST "/file/move" request
        (trap "move-files" 
               do-move request irods-actions/move-files))
  
  (GET "/file/download" request
       (trap "download" 
              do-download request))
  
  (GET "/file/preview" request
       (trap "preview" 
              do-preview request))
  
  (GET "/file/manifest" request
       (trap "manifest" 
              do-manifest request))
  
  (GET "/file/metadata" request
       (trap "get-metadata" 
              do-metadata-get request))
  
  (GET "/file/tree-urls" request
       (trap "get-tree-urls" 
              do-tree-get request))
  
  (GET "/directory/metadata" request
       (trap "get-metadata" 
              do-metadata-get request))
  
  (POST "/file/metadata" request
        (trap "set-metadata" 
               do-metadata-set request))
  
  (POST "/file/metadata-batch" request
        (trap "set-metadata-batch" 
               do-metadata-batch-set request))
  
  (POST "/file/tree-urls" request
        (trap "set-tree-urls" 
               do-tree-set request))
  
  (POST "/directory/metadata" request
        (trap "set-metadata" 
               do-metadata-set request))
  
  (POST "/directory/metadata-batch" request
        (trap "set-metadata-batch" 
               do-metadata-batch-set request))
  
  (DELETE "/file/metadata" request
          (trap "delete-metadata" 
                 do-metadata-delete request))
  
  (DELETE "/directory/metadata" request
          (trap "delete-metadata" 
                 do-metadata-delete request))
  
  (POST "/share" request
        (trap "share" do-share request))

  (POST "/unshare" request
        (trap "unshare" do-unshare request))

  (POST "/user-permissions" request
       (trap "user-permissions" do-user-permissions request))
  
  (route/not-found "Not Found!"))

(defn site-handler [routes]
  (-> routes
    jb/parse-json-body
    wrap-multipart-params
    wrap-keyword-params
    wrap-nested-params
    qp/wrap-query-params
    wrap-stacktrace))

(defn parse-args
  [args]
  (cli/cli
   args
    ["-c" "--config" 
     "Set the local config file to read from. Bypasses Zookeeper" 
     :default nil]
    ["-h" "--help" 
     "Show help." 
     :default false 
     :flag true]
    ["-p" "--port" 
     "Set the port to listen on." 
     :default 31370 
     :parse-fn #(Integer. %)]))

(def app
  (site-handler nibblonian-routes))

(defn -main
  [& args]
  
  (let [[opts args help-str] (parse-args args)]
    (cond
      (:help opts)
      (do (println help-str)
        (System/exit 0)))

    (if (:config opts)
      (local-init (:config opts))
      (init))
  
    (jetty/run-jetty app {:port (listen-port)})))
