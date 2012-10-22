(ns nibblonian.controllers
  (:use nibblonian.error-codes
        nibblonian.request-utils
        nibblonian.config
        clojure.java.classpath
        [slingshot.slingshot :only [try+ throw+]])
  (:require [clojure-commons.props :as prps]
            [nibblonian.irods-actions :as irods-actions]
            [ring.util.response :as rsp-utils]
            [ring.util.codec :as cdc]
            [clojure-commons.file-utils :as utils]
            [clj-jargon.jargon :as jargon]
            [nibblonian.irods-actions :as irods-actions]
            [clojure-commons.clavin-client :as cl]
            [clojure.tools.logging :as log]
            [clojure.data.json :as json]
            [clojure.string :as string]))

(defn super-user?
  [username]
  (.equals username (irods-user)))

(defn- dir-list
  ([user directory include-files]
     (dir-list user directory include-files true))
  
  ([user directory include-files set-own?]
     (when (super-user? user)
       (throw+ {:error_code ERR_NOT_AUTHORIZED
                :user user}))
     
     (let [comm-dir   (community-data)
           user-dir   (utils/path-join (irods-home) user)
           public-dir (utils/path-join (irods-home) "public")
           files-to-filter (conj
                            (filter-files)
                            comm-dir
                            user-dir
                            public-dir)]
       (irods-actions/list-dir
        user
        directory
        include-files
        files-to-filter
        set-own?))))

(defn do-homedir
  "Returns the home directory for the listed user."
  [request]
  (log/debug "do-homedir")
  (when-not (query-param? request "user")
    (bad-query "user" "home"))
  (let [user       (query-param request "user")]
    (irods-actions/user-home-dir (irods-home) user false)))

(defn- get-home-dir
  [user]
  (irods-actions/user-home-dir (irods-home) user true))

(defn- include-files [file-param] (not= "0" file-param))

(defn- include-files?
  [request]
  (if (query-param? request "includefiles")
    (include-files (query-param request "includefiles"))
    false))

(defn- gen-comm-data
  [user inc-files]
  (let [cdata (dir-list user (community-data) inc-files)]
    (assoc cdata :label "Community Data")))

(defn- gen-sharing-data
  [user inc-files]
  (let [comm-dir   (community-data)
        user-dir   (utils/path-join (irods-home) user)
        public-dir (utils/path-join (irods-home) "public")
        files-to-filter (conj (filter-files) comm-dir user-dir public-dir)]
    (irods-actions/shared-root-listing user (irods-home) inc-files files-to-filter)))

(defn do-directory
  "Performs a list-dirs command.

   Request Parameters:
     user - Query string value containing a username."
  [request]
  (log/debug "do-directory")
  (when-not (query-param? request "user")
    (bad-query "user"))
  
  ;;; If there's no path parameter, then it's a top-level
  ;;; request and the community listing should be included.
  (cond 
    (not (query-param? request "path")) 
    (let [user       (query-param request "user")
          inc-files  (include-files? request)
          comm-f     (future (gen-comm-data user inc-files))
          share-f    (future (gen-sharing-data user inc-files))
          home-f     (future (dir-list user (get-home-dir user) inc-files))]
      {:roots [@home-f @comm-f @share-f]})
    
    :else
    ;;; There's a path parameter, so simply list the directory.  
    (let [user      (query-param request "user")
          path      (query-param request "path")
          inc-files (include-files? request)]
      (if (irods-actions/user-trash-dir? user path)
        (dir-list user path inc-files true)
        (dir-list user path inc-files)))))

(defn do-root-listing
  [request]
  (log/debug "do-root-listing")
  
  (when-not (query-param? request "user")
    (bad-query "user"))
  
  (let [user  (query-param request "user")
        uhome (utils/path-join (irods-home) user)
        user-root-list (partial irods-actions/root-listing user)
        user-trash-dir (irods-actions/user-trash-dir user)]
    {:roots
     [(user-root-list uhome)
      (user-root-list (community-data))
      (user-root-list (irods-home))
      (user-root-list user-trash-dir true)]}))

(defn do-rename
  "Performs a rename.

   Function Parameters:
     request - Ring request map.
     rename-func - The rename function to call.

   Request Parameters:
     user - Query string value containing a username.
     dest - JSON field from the body telling what to rename the file to.
     source - JSON field from the body telling which file to rename."
  [request]
  (log/debug "do-rename")
  
  (when-not (query-param? request "user")
    (bad-query "user"))
  
  (when-not (valid-body? request {:source string? :dest string?})
    (bad-body request {:source string? :dest string?})) 
  
  (let [body-json (:body request)
        user      (query-param request "user")
        dest      (:dest body-json)
        source    (:source body-json)]
    (log/info (str "Body: " (json/json-str body-json)))
    
    (when (super-user? user)
      (throw+ {:error_code ERR_NOT_AUTHORIZED           
               :user user}))
    (irods-actions/rename-path user source dest)))

(defn do-delete
  "Performs a delete.

   Function Parameters:
     request - Ring request map.
     delete-func - The deletion function to call.

   Request Parameters:
     user - Query string value containing a username.
     paths - JSON field containing a list of paths that should be deleted."
  [request]
  (log/debug "do-delete")
  
  (when-not (query-param? request "user")
    (bad-query "user"))
  
  (when-not (valid-body? request {:paths sequential?})
    (bad-body request {:paths sequential?})) 
  
  (let [body-json (:body request)
        user      (query-param request "user")
        paths     (:paths body-json)]
    (log/info (str "Body: " (json/json-str body-json)))

    (when (super-user? user)
      (throw+ {:error_code ERR_NOT_AUTHORIZED
               :user user}))
    (irods-actions/delete-paths user paths)))

(defn do-move
  "Performs a move.

   Function Parameters:
     request - Ring request map.
     move-func - The move function to call.

   Request Parameters:
     user - Query string value containing a username.
     sources - JSON field containing a list of paths that should be moved.
     dest - JSON field containing the destination path."
  [request]
  (log/debug "do-move")

  (when-not (query-param? request "user")
    (bad-query "user"))
  
  (let [check-map {:sources sequential? :dest string?}] 
    (when-not (valid-body? request check-map)
      (bad-body request check-map)))
  
  (let [body-json (:body request)
        user      (query-param request "user")
        sources   (:sources body-json)
        dest      (:dest body-json)]
    (log/info (str "Body: " (json/json-str body-json)))
    
    (when (super-user? user)
      (throw+ {:error_code ERR_NOT_AUTHORIZED :user user}))
    
    (irods-actions/move-paths user sources dest)))

(defn do-create
  "Performs a directory creation.

   Function Parameters:
     request - Ring request map.

   Request Parameters:
     user - Query string value containing a username.
     path - JSON field containing the path to create."
  [request]
  (log/debug "do-create")  

  (when-not (query-param? request "user")
    (bad-query "user"))
  
  (when-not (valid-body? request {:path string?})
    (bad-body request {:path string?})) 
  
  (let [body-json (:body request)
        user      (query-param request "user")
        path      (:path body-json)]
    (log/info (str "Body: " body-json))

    (when (super-user? user)
      (throw+ {:error_code ERR_NOT_AUTHORIZED :user user}))

    (irods-actions/create user path)))

(defn do-metadata-get
  [request]
  (log/debug "do-metadata-get")

  (when-not (query-param? request "user") 
    (bad-query "user"))
  
  (when-not (query-param? request "path") 
    (bad-query "user"))
  
  (let [user (query-param request "user")
        path (query-param request "path")]
    (irods-actions/metadata-get user path)))

(defn do-tree-get
  [request]
  (log/debug "do-tree-get")

  (when-not (query-param? request "user") 
    (bad-query "user"))
  
  (when-not (query-param? request "path") 
    (bad-query "user"))
  
  (let [user (query-param request "user")
        path (query-param request "path")]
    (irods-actions/get-tree user path)))

(defn do-metadata-set
  [request]
  (log/debug "do-metadata-set")

  (when-not (query-param? request "user") 
    (bad-query "user"))
  
  (when-not (query-param? request "path") 
    (bad-query "path"))
  
  (let [check-map {:attr string? :value string? :unit string?}] 
    (when-not (valid-body? request check-map)
      (bad-body request check-map)))
  
  (let [user (query-param request "user")
        path (query-param request "path")
        body (:body request)]
    (log/info (str "Body: " (json/json-str body)))
    (irods-actions/metadata-set user path body)))

(defn- fix-username
  [username]
  (if (re-seq #"@" username)
    (subs username 0 (.indexOf username "@"))
    username))

(defn do-share
  [request]
  (log/debug "do-share")
  
  (when-not (query-param? request "user")
    (bad-query "user"))
  
  (let [check-map {:paths sequential? :users sequential? :permissions map?}]
    (when-not (valid-body? request check-map)
      (bad-body request check-map)))
  
  (let [user        (fix-username (query-param request "user"))
        share-withs (map fix-username (get-in request [:body :users]))
        fpaths      (get-in request [:body :paths])
        perms       (get-in request [:body :permissions])]
    (when-not (contains? perms :read)
      (throw+ {:error_code ERR_BAD_OR_MISSING_FIELD
               :field "read"}))
    
    (when-not (contains? perms :write)
      (throw+ {:error_code ERR_BAD_OR_MISSING_FIELD
               :field "write"}))
    
    (when-not (contains? perms :own)
      (throw+ {:error_code ERR_BAD_OR_MISSING_FIELD
               :field "write"}))
    
    (irods-actions/share user share-withs fpaths perms)))

(defn do-unshare
  [request]
  (log/debug "do-unshare")

  (when-not (query-param? request "user")
    (bad-query "user"))

  (let [check-map {:paths sequential? 
                   :users sequential?}] 
    (when-not (valid-body? request check-map)
      (bad-body request check-map)))

  (let [user        (fix-username (query-param request "user"))
        share-withs (map fix-username (get-in request [:body :users]))
        fpaths      (get-in request [:body :paths])]
    (irods-actions/unshare user share-withs fpaths)))

(defn- check-adds
  [adds]
  (mapv #(= (set (keys %)) (set [:attr :value :unit])) adds))

(defn- check-dels
  [dels]
  (mapv string? dels))

(defn do-metadata-batch-set
  [request]
  (log/debug "do-metadata-set")
  
  (when-not (query-param? request "user")
    (bad-query "user"))
  
  (when-not (query-param? request "path")
    (bad-query "path"))
  
  (when-not (valid-body? request {:add sequential?})
    (bad-body request {:add sequential?}))
  
  (when-not (valid-body? request {:delete sequential?})
    (bad-body request {:delete sequential?}))
  
  (let [user (query-param request "user")
        path (query-param request "path")
        body (:body request)
        adds (:add body)
        dels (:delete body)]
    (when (pos? (count adds))
      (if (not (every? true? (check-adds adds)))
        (throw+ {:error_code ERR_BAD_OR_MISSING_FIELD :field "add"})))
    
    (when (pos? (count dels))
      (if (not (every? true? (check-dels dels)))
        (throw+ {:error_code ERR_BAD_OR_MISSING_FIELD :field "add"})))

    (irods-actions/metadata-batch-set user path body)))

(defn do-tree-set
  [request]
  (log/debug "do-tree-set")

  (when-not (query-param? request "user") 
    (bad-query "user"))
  
  (when-not (query-param? request "path")
    (bad-query "path"))
  
  (when-not (valid-body? request {:tree-urls vector})
    (bad-body request {:tree-urls vector}))
  
  (let [user (query-param request "user")
        path (query-param request "path")
        body (:body request)]
    (log/info (str "Body: " (json/json-str body)))

    (irods-actions/set-tree user path body)))

(defn do-metadata-delete
  [request]
  (log/debug "do-metadata-delete")

  (when-not (query-param? request "user") 
    (bad-query "user"))
  
  (when-not (query-param? request "path") 
    (bad-query "path"))
  
  (when-not (query-param? request "attr") 
    (bad-query "attr"))
  
  (let [user (query-param request "user")
        path (query-param request "path")
        attr (query-param request "attr")]
    (irods-actions/metadata-delete user path attr)))

(defn do-preview
  "Handles a file preview.

   Request Parameters:
     user - Query string field containing a username.
     path - Query string field containing the file to preview."
  [request]
  (log/debug "do-preview")

  (when-not (query-param? request "user")
    (bad-query "user"))
  
  (when-not (query-param? request "path")
    (bad-query "path"))
  
  (let [user (query-param request "user")
        path (query-param request "path")]
    (when (super-user? user)
      (throw+ {:error_code ERR_NOT_AUTHORIZED :user user :path path}))
    {:action "preview"
     :preview (irods-actions/preview user path (preview-size))}))

(defn do-exists
  "Returns True if the path exists and False if it doesn't."
  [request]
  (log/debug "do-exists")

  (when-not (query-param? request "user")
    (bad-query "user"))
  
  (when-not (valid-body? request {:paths vector?})
    (bad-body request {:paths vector?}))
  
  (let [paths (:paths (:body request))
        user  (query-param request "user")]
    {:paths (apply conj {} (map #(hash-map %1 (irods-actions/path-exists? user %1)) paths))}))

(defn do-stat
  "Returns data object status information for one or more paths."
  [request]
  (log/debug "do-stat")

  (when-not (query-param? request "user")
    (bad-query "user"))

  (when-not (valid-body? request {:paths vector?})
    (bad-body request {:paths vector?}))

  (let [paths (:paths (:body request))
        user  (query-param request "user")]
    {:paths (into {} (map #(vector % (irods-actions/path-stat user %)) paths))}))

(defn do-manifest
  "Returns a manifest consisting of preview and rawcontent fields for a
   file."
  [request]
  (log/debug "do-manifest")

  (when-not (query-param? request "user") 
    (bad-query "user"))
  
  (when-not (query-param? request "path") 
    (bad-query "path"))
  
  (let [user (query-param request "user")
        path (query-param request "path")]
    (irods-actions/manifest user path (data-threshold))))

(defn do-download
  [request]
  (when-not (query-param? request "user") 
    (bad-query "user"))
  
  (when-not (valid-body? request {:paths sequential?})
    (bad-body request {:paths sequential?}))
  
  (let [user      (query-param request "user")
        filepaths (:paths (:body request))]
    (irods-actions/download user filepaths)))

(defn do-upload
  [request]
  (when-not (query-param? request "user")
    (bad-query "user"))
  
  (let [user (query-param request "user")]
    (irods-actions/upload user)))

(defn do-special-download
  "Handles a file download

   Request Parameters:
     user - Query string field containing a username.
     path - Query string field containing the path to download."
  [request]
  (log/debug "do-download")  

  (when-not (query-param? request "user") 
    (bad-query "user"))
  
  (when-not (query-param? request "path") 
    (bad-query "path"))
  
  (let [user (query-param request "user")
        path (query-param request "path")]
    (log/info (str "User for download: " user))
    (log/info (str "Path to download: " path))
    
    (when (super-user? user)
      (throw+ {:error_code ERR_NOT_AUTHORIZED :user user}))

    (cond
      ;;; If disable is not included, assume the attachment
      ;;; part should be left out.
      (not (query-param? request "attachment"))
      (rsp-utils/header
       {:status 200 :body (irods-actions/download-file user path)}
       "Content-Disposition"
       (str "attachment; filename=\"" (utils/basename path) "\""))
      
      (not (attachment? request))
      (rsp-utils/header
       {:status 200 :body (irods-actions/download-file user path)}
       "Content-Disposition"
       (str "filename=\"" (utils/basename path) "\""))
      
      :else
      (rsp-utils/header
       {:status 200 :body (irods-actions/download-file user path)}
       "Content-Disposition"
       (str "attachment; filename=\"" (utils/basename path) "\"")))))

(defn do-user-permissions
  "Handles returning the list of user permissions for a file
   or directory.

   Request parameters:
      user - Query string field containing the username of the user
             making the request.
      path - Query string field containin the path to the file."
  [request]
  (log/debug "do-user-permissions")

  (when-not (query-param? request "user")
    (bad-query "user"))

  (when-not (valid-body? request {:paths sequential?})
    (bad-body request {:paths sequential?}))

  (let [user  (query-param request "user")
        paths (get-in request [:body :paths])]
    {:paths (irods-actions/list-perms user paths)}))

(defn do-restore
  "Handles restoring a file or directory from a user's trash directory."
  [request]
  (log/debug "do-restore")

  (when-not (query-param? request "user")
    (bad-query "user"))

  (when-not (valid-body? request {:paths sequential?})
    (bad-body request {:paths string?}))

  #_(when-not (valid-body? request {:name string?})
    (bad-body request {:name string?}))

  (let [user (query-param request "user")]
    (irods-actions/restore-path
     {:user user
      :paths (get-in request [:body :paths])
      #_(:name (get-in request [:body :name]))
      :user-trash (irods-actions/user-trash-dir user)})))

(defn do-copy
  [request]
  (log/debug "do-copy")

  (when-not (query-param? request "user")
    (bad-query "user"))

  (when-not (valid-body? request {:paths sequential?})
    (bad-body request {:paths sequential?}))

  (when-not (valid-body? request {:destination string?})
    (bad-body request {:destination string?}))

  (irods-actions/copy-path
   {:user (query-param request "user")
    :from (get-in request [:body :paths])
    :to   (get-in request [:body :destination])}
   (copy-key)))

(defn do-quota
  "Handles returning a list of objects representing
   all of the quotas that a user has."
  [request]
  (log/debug "do-quota")
  
  (when-not (query-param? request "user")
    (bad-query "user"))
  
  (let [user (query-param request "user")]
    {:quotas (irods-actions/get-quota user)}))

(defn do-user-trash
  [request]
  (log/debug "do-user-trash")

  (when-not (query-param? request "user")
    (bad-query "user"))

  (let [user (query-param request "user")]
    (irods-actions/user-trash user)))
