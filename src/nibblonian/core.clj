(ns nibblonian.core
  (:gen-class)
  (:use compojure.core)
  (:use nibblonian.request-utils
        nibblonian.controllers
        nibblonian.config
        nibblonian.error-codes
        lamina.core
        [ring.middleware
         params
         keyword-params
         nested-params
         multipart-params
         cookies
         session
         stacktrace]
        [slingshot.slingshot :only [try+ throw+]])
  (:require [clojure.tools.cli :as cli]
            [compojure.route :as route]
            [compojure.handler :as handler]
            [nibblonian.query-params :as qp]
            [nibblonian.json-body :as jb]
            [clojure.tools.logging :as log]
            [ring.adapter.jetty :as jetty]
            [nibblonian.irods-actions :as irods-actions])
  (:import [org.irods.jargon.core.exception
            JargonRuntimeException
            JargonException]))

(defroutes nibblonian-routes
  (GET "/" [] "Welcome to Nibblonian!")

  (GET "/root" request
       (trap "root" do-root-listing request))

  (GET "/home" request
       (trap "home" do-homedir request))

  (POST "/exists" request
        (trap "exists" do-exists request))

  (POST "/stat" request
        (trap "stat" do-stat request))

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

  (POST "/rename" request
        (trap "rename" do-rename request))

  (POST "/delete" request
        (trap "delete" do-delete request))

  (POST "/move" request
        (trap "move" do-move request))

  (GET "/file/download" request
       (trap "download"
              do-download request))

  (GET "/file/preview" request
       (trap "preview"
             do-preview request))

  (GET "/file/manifest" request
       (trap "manifest"
             do-manifest request))

  (GET "/metadata" request
       (trap "get-metadata"
             do-metadata-get request))

  (POST "/metadata" request
        (trap "set-metadata"
              do-metadata-set request))

  (DELETE "/metadata" request
          (trap "delete-metadata"
                do-metadata-delete request))

  (POST "/metadata-batch" request
        (trap "set-metadata-batch"
              do-metadata-batch-set request))

  (POST "/share" request
        (trap "share" do-share request))

  (POST "/unshare" request
        (trap "unshare" do-unshare request))

  (POST "/user-permissions" request
       (trap "user-permissions" do-user-permissions request))

  (GET "/groups" request
       (trap "groups" do-groups request))

  (GET "/quota" request
       (trap "quota-list" do-quota request))

  (POST "/restore" request
        (trap "restore" do-restore request))

  (POST "/copy" request
        (trap "copy" do-copy request))

  (POST "/tickets" request
        (trap "add-tickets" do-add-tickets request))

  (POST "/delete-tickets" request
        (trap "delete-tickets" do-remove-tickets request))

  (POST "/list-tickets" request
        (trap "list-tickets" do-list-tickets request))

  (GET "/user-trash-dir" request
       (trap "user-trash-dir" do-user-trash request))

  (DELETE "/trash" request
          (trap "delete-trash" do-delete-trash request))

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
