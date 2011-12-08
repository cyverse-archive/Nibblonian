(ns nibblonian.core
  (:use compojure.core)
  (:use [ring.middleware
         params
         keyword-params
         nested-params
         multipart-params
         cookies
         session]
        [clojure.contrib.java-utils]
        [clojure.contrib.classpath]
        [nibblonian.error-codes])
  (:require [compojure.route :as route]
            [compojure.handler :as handler]
            [ring.util.response :as rsp-utils]
            [ring.util.codec :as cdc]
            [nibblonian.utils :as utils]
            [nibblonian.irods-base :as irods-base]
            [nibblonian.irods-actions :as irods-actions]
            [nibblonian.query-params :as qp]
            [nibblonian.json-body :as jb]
            [clojure.contrib.json :as json]
            [clojure.contrib.duck-streams :as ds]
            [clojure.contrib.logging :as log]
            [clojure.contrib.string :as string]
            [swank.swank])
  (:import [org.irods.jargon.core.exception JargonRuntimeException JargonException]))

(defn find-properties-file
  "Searches the classpath for the named properties file."
  [prop-name]
  (. (. (. (. Thread currentThread) getContextClassLoader) getResource prop-name) getFile))

; Reads in the properties file and assigns props to the map
(def props
  (let [prop-filename "nibblonian.properties"
        prop-path (find-properties-file prop-filename)
        prop (if (nil? prop-path) "resources/nibblonian.properties" prop-path)]
    (read-properties prop)))

(def max-retries
  (java.lang.Integer/parseInt
    (get props "nibblonian.app.max-retries")))

(def retry-sleep
  (java.lang.Integer/parseInt
    (get props "nibblonian.app.retry-sleep")))

(def preview-size
  (java.lang.Integer/parseInt
    (get props "nibblonian.app.preview-size")))

(def data-threshold
  (java.lang.Integer/parseInt
    (get props "nibblonian.app.data-threshold")))

(def community-data
  (get props "nibblonian.app.community-data"))

; Sets up the connection to iRODS through jargon-core.
(irods-base/init-irods
 (get props "nibblonian.irods.host")
 (get props "nibblonian.irods.port")
 (get props "nibblonian.irods.user")
 (get props "nibblonian.irods.password")
 (get props "nibblonian.irods.home")
 (get props "nibblonian.irods.zone")
 (get props "nibblonian.irods.defaultResource"))

;;; Sets up the embedded swank REPL.
(if (contains? props "nibblonian.swank.enabled")
  (let [enabled (get props "nibblonian.swank.enabled")
        swank-port (if (contains? props "nibblonian.swank.port")
              (get props "nibblonian.swank.port") nil)]
    (if (. enabled equals "true")
      (if (not (nil? swank-port))
        (swank.swank/start-repl swank-port)
        (swank.swank/start-repl)))))

(defn json?
  "Checks to make sure that a string contains JSON."
  [a-string]
  (log/debug (str "json? " a-string))
  (if (try
        (json/read-json a-string)
        (catch Exception e false))
    true false))

(defn invalid-fields
  "Validates the format of a map against a spec.

   map-spec is a map where the key is the name of a
   corresponding field in a-map that must exist. The
   value is a function that returns true or false
   when the corresponding value in a-map is passed into
   it.

   Returns a sequence of field-names from 'a-map'that
   aren't compliant with the spec. They're either missing
   or the validator function returned false when the
   value was passed in."
  [a-map map-spec]
  (log/debug (str "invalid-fields " a-map " " map-spec))
  (filter (fn [f] (not (nil? f)))
          (for [[field-name validator?] map-spec]
            (if (contains? a-map field-name)
              (if (validator? (get a-map field-name)) nil field-name)
              field-name))))

(defn map-is-valid?
  "Returns true if the 'a-map' conforms to 'map-spec'."
  [a-map map-spec]
  (log/debug (str "map-is-valid? " a-map " " map-spec))
  (if (map? a-map)
    (== 0 (count (invalid-fields a-map map-spec)))
    false))

(defn parse-json
  "Parses a JSON string into a map. Performs error-checking."
  [json-string map-spec]
  (log/debug (str "parse-json " json-string " " map-spec))
  (if (json? json-string)
    (let [obj (json/read-json json-string)]
      (if (map-is-valid? obj map-spec)
        obj
        {:status "failure"
         :reason "Bad or missing field"
         :error_code ERR_BAD_OR_MISSING_FIELD
         :fields (invalid-fields obj map-spec)}))
    {:status "failure" 
     :reason "Invalid JSON" 
     :error_code ERR_INVALID_JSON}))

(defn query-param
  "Grabs the 'field' from the query string associated
   with the request and returns it.

   Parameters:
      request - request map put together by Ring.
      field - name of the query value to return.
   "
  [request field]
  (log/debug (str "query-param " field))
  (get (:query-params request) field))

(defn query-param?
  "Checks to see if the specified query-param actually exists
   in the request.

   Parameters:
      request - request map put together by Ring.
      field - name of the query key to check for."
  [request field]
  (log/debug (str "query-param?" field))
  (contains? (:query-params request) field))

(defn form-param
  "Grabs the 'field' from the form-data associated with
   the request and returns it.

   Parameters:
     request - request map put together by Ring.
     field - name of the form-data value to return."
  [request field]
  (log/debug (str "form-param " field))
  (get (:params request) field))

(defn is-failed?
  "Checks the map 'result-msg' to see if it represents
   a failed jargon-core call."
  [result-msg]
  (log/debug (str "is-failed? " result-msg))
  (= "failure" (:status result-msg)))

(defn create-response
  "Creates a Ring-compatible response map from the 'results' map returned
   by the calls into irods."
  ([results] (create-response results "text/plain"))
  ([results content-type]
    (log/debug (str "create-response " results))
    (let [status (if (not (is-failed? results)) 200 400)
          body (json/json-str results)
          retval (merge
                   (rsp-utils/content-type (rsp-utils/response "") content-type)
                   {:status status :body body})]
      (log/info (str "Returning " (json/json-str retval)))
      retval)))

(defn valid-body?
  [request body-spec]
  (cond
    (not (map? (:body request)))
    false
    
    (not (map-is-valid? (:body request) body-spec))
    false
    
    :else
    true))

(defn bad-body 
  [request body-spec]
  (cond
    (not (map? (:body request)))
    {:status "failure"
     :action "body-check"
     :error_code ERR_INVALID_JSON
     :reason "Invalid JSON"}
    
    (not (map-is-valid? (:body request) body-spec))
    {:status "failure"
     :reason "Bad or missing field"
     :error_code ERR_BAD_OR_MISSING_FIELD
     :fields (invalid-fields  (:body request) body-spec)}
    
    :else
    {:status "success"}))

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
      (if (and (not (:succeeded attempt)) (< num-tries max-retries))
	(do (Thread/sleep retry-sleep)
   (recur (+ num-tries 1)))
	(if (:succeeded attempt)
	  (:retval attempt)
	  (throw (:exception attempt)))))))

(defn super-user?
  [username]
  (. username equals (get props "nibblonian.irods.user")))

(defn- dir-list
  [user directory include-files]
  (if (not (super-user? user))
    (irods-actions/list-dir user directory include-files)
    {:action "list-dir"
     :status "failure"
     :error_code ERR_NOT_AUTHORIZED
     :reason "not allowed for that user"
     :user user}))

(defn bad-query [key action]
  (create-response
    {:action action
     :status "failure"
     :error_code ERR_MISSING_QUERY_PARAMETER
     :reason (str "missing " key " query parameter")}))

(defn do-homedir
  "Returns the home directory for the listed user."
  [request]
  (log/debug "do-homedir")
  (if (not (query-param? request "user"))
    (bad-query "user" "home")
    (let [user (query-param request "user")]
      (irods-actions/user-home-dir (get props "nibblonian.irods.home") user false))))

(defn- get-home-dir
  [user]
  (irods-actions/user-home-dir (get props "nibblonian.irods.home") user true))

(defn- include-files
  [file-param]
  (not (= "0" file-param)))

(defn do-directory
  "Performs a list-dirs command.

   Request Parameters:
     user - Query string value containing a username."
  [request]
  (log/debug "do-directory")
  (cond 
    (not (query-param? request "user"))
    (bad-query "user" "dir-list")
    
    ;;; If there's no path parameter, then it's a top-level
    ;;; request and the community listing should be included.
    (not (query-param? request "path")) 
    (let [user      (query-param request "user")
          inc-files (if (query-param? request "includefiles")
                      (include-files (query-param request "includefiles"))
                      false)
          comm-data (assoc (dir-list user community-data inc-files) :label "Community Data")
          home-data (dir-list user (get-home-dir user) inc-files) 
          status    (if (or (= (:status comm-data) "failure") (= (:status home-data) "failure"))
                      "failure"
                      "success")]
      (create-response
        {:status status
         :roots [home-data comm-data]}))
    
    ;;; There's a path parameter, so simply list the directory.  
    :else 
    (let [user      (query-param request "user")
          path      (query-param request "path")
          inc-files (if (query-param? request "includefiles")
                      (include-files (query-param request "includefiles"))
                      false)]
      (create-response (dir-list user path inc-files)))))

(defn do-rename
  "Performs a rename.

   Function Parameters:
     request - Ring request map.
     rename-func - The rename function to call.

   Request Parameters:
     user - Query string value containing a username.
     dest - JSON field from the body telling what to rename the file to.
     source - JSON field from the body telling which file to rename."
  [request rename-func]
  (log/debug "do-rename")
  (cond
    (not (query-param? request "user"))
    (bad-query "user" "rename")
    
    (not (valid-body? request {:source string? :dest string?}))
    (create-response (bad-body request {:source string? :dest string?})) 
    
    :else
    (let [body-json (:body request)
          user (query-param request "user")
          dest (:dest body-json)
          source (:source body-json)]
      (log/info (str "Body: " (json/json-str body-json)))
      (cond
        (super-user? user)
        (create-response
          {:action "rename"
           :status "failure"
           :error_code ERR_NOT_AUTHORIZED
           :reason "not allowed for that user"
           :user user})
        
        (= "failure" (:status body-json))
        (create-response (merge {:action "rename"} body-json))
        
        :else
        (create-response (rename-func user source dest))))))

(defn do-delete
  "Performs a delete.

   Function Parameters:
     request - Ring request map.
     delete-func - The deletion function to call.

   Request Parameters:
     user - Query string value containing a username.
     paths - JSON field containing a list of paths that should be deleted."
  [request delete-func]
  (log/debug "do-delete")
  (cond
    (not (query-param? request "user"))
    (bad-query "user" "delete")
    
    (not (valid-body? request {:paths sequential?}))
    (create-response (bad-body request {:paths sequential?})) 
    
    :else
    (let [body-json (:body request)
          user (query-param request "user")
          paths (:paths body-json)]
      (log/info (str "Body: " (json/json-str body-json)))
      (cond
        (super-user? user)
        (create-response
          {:action "delete"
           :status "failure"
           :error_code ERR_NOT_AUTHORIZED
           :reason "action not allowed for user"
           :user user})
        
        (= "failure" (:status body-json))
        (create-response (merge {:action "delete"} body-json))
        
        :else
        (create-response (delete-func user paths)))))) ()

(defn do-move
  "Performs a move.

   Function Parameters:
     request - Ring request map.
     move-func - The move function to call.

   Request Parameters:
     user - Query string value containing a username.
     sources - JSON field containing a list of paths that should be moved.
     dest - JSON field containing the destination path."
  [request move-func]
  (log/debug "do-move")
  (cond
    (not (query-param? request "user"))
    (bad-query "user" "move")
    
    (not (valid-body? request {:sources sequential? :dest string?}))
    (create-response (bad-body request {:sources sequential? :dest string?}))
    
    :else
    (let [body-json (:body request)
          user (query-param request "user")
          sources (:sources body-json)
          dest (:dest body-json)]
      (log/info (str "Body: " (json/json-str body-json)))
      (cond
        (super-user? user)
        (create-response
          {:action "move"
           :status "failure"
           :error_code ERR_NOT_AUTHORIZED
           :reason "action not allowed by user"
           :user user})
        
        (= "failure" (:status body-json))
        (create-response (merge {:action "move"} body-json))
        
        :else
        (create-response (move-func user sources dest))))))

(defn do-urlupload
  "Performs a url upload.

   Function Parameters:
     request - Ring request map.

   Request Parameters:
     user - Query string value containing a username.
     dest - JSON field containing the destination path.
     address - JSON field containing the URL to slurp in."
  [request]
  (log/debug "do-urlupload")
  (log/debug (str "BODY:  " (:body request)))
  (cond
    (not (query-param? request "user"))
    (bad-query "user" "urlupload")
    
    (not (valid-body? request {:dest string? :address string?}))
    (create-response (bad-body request {:dest string? :address string?})) 
    
    :else
    (let [body-json (:body request)
          user (query-param request "user")
          dest (:dest body-json)
          address (:address body-json)]
      (log/debug (str "Body: " (json/json-str body-json)))
      
      (cond
        (super-user? user)
        (create-response
          {:action "urlupload"
           :status "failure"
           :error_code ERR_NOT_AUTHORIZED
           :reason "action not allowed by that user"
           :user user})
        
        (= "failure" (:status body-json))
        (create-response (merge {:action "urlupload"} body-json))
        
        :else
        (create-response (irods-actions/load-from-url user (utils/dirname dest) (utils/basename dest) address))))))

(defn do-create
  "Performs a directory creation.

   Function Parameters:
     request - Ring request map.

   Request Parameters:
     user - Query string value containing a username.
     path - JSON field containing the path to create."
  [request]
  (log/debug "do-create")
  
  (cond
    (not (query-param? request "user"))
    (bad-query "user" "create")
    
    (not (valid-body? request {:path string?}))
    (create-response (bad-body request {:path string?})) 
    
    :else
    (let [body-json (:body request)
          user (query-param request "user")
          path (:path body-json)]
      (log/info (str "Body: " body-json))
      (cond
        (super-user? user)
        (create-response
          {:action "create"
           :status "failure"
           :error_code ERR_NOT_AUTHORIZED
           :reason "action not allowed by that user"
           :user user})
        
        (= "failure" (:status body-json))
        (create-response (merge {:action "create"} body-json))
        
        :else
        (create-response (irods-actions/create user path))))))

(defn fail-resp
  [action status reason]
  (create-response {:action action :status status :reason reason}))

(defn do-metadata-get
  [request]
  (log/debug "do-metadata-get")
  (cond
    (not (query-param? request "user")) (bad-query "user" "get-metadata")
    (not (query-param? request "path")) (bad-query "user" "get-metadata")
    :else
    (let [user (query-param request "user")
          path (query-param request "path")]
      (create-response (irods-actions/metadata-get user path)))))

(defn do-tree-get
  [request]
  (log/debug "do-tree-get")
  (cond
    (not (query-param? request "user")) (bad-query "user" "get-tree-urls")
    (not (query-param? request "path")) (bad-query "user" "get-tree-urls")
    :else
    (let [user (query-param request "user")
          path (query-param request "path")]
      (create-response (irods-actions/get-tree user path)))))

(defn do-metadata-set
  [request]
  (log/debug "do-metadata-set")
  (cond
    (not (query-param? request "user")) 
    (bad-query "user" "set-metadata")
    
    (not (query-param? request "path")) 
    (bad-query "path" "set-metadata")
    
    (not (valid-body? request {:attr string? :value string? :unit string?}))
    (create-response (bad-body request {:attr string? :value string? :unit string?}))
    
    :else
    (let [user (query-param request "user")
          path (query-param request "path")
          body (:body request)]
      (log/info (str "Body: " (json/json-str body)))
      (create-response (irods-actions/metadata-set user path body)))))

(defn do-tree-set
  [request]
  (log/debug "do-tree-set")
  (cond
    (not (query-param? request "user")) 
    (bad-query "user" "set-tree-urls")
    
    (not (query-param? request "path"))
    (bad-query "path" "set-tree-urls")
    
    (not (valid-body? request {:tree-urls vector}))
    (create-response (bad-body request {:tree-urls vector}))
    
    :else
    (let [user (query-param request "user")
          path (query-param request "path")
          body (:body request)]
      (log/info (str "Body: " (json/json-str body)))
      (create-response (irods-actions/set-tree user path body)))))

(defn do-metadata-delete
  [request]
  (log/debug "do-metadata-delete")
  (cond
    (not (query-param? request "user")) (bad-query "user" "delete-metadata")
    (not (query-param? request "path")) (bad-query "path" "delete-metadata")
    (not (query-param? request "attr")) (bad-query "attr" "delete-metadata")
    :else
    (let [user (query-param request "user")
          path (query-param request "path")
          attr (query-param request "attr")]
      (create-response (irods-actions/metadata-delete user path attr)))))

(defn multipart-inputfile
  "Extracts the location of an uploaded files temp location from
   the Ring request map."
  [request]
  (log/debug "multipart-inputfile")
  (:tempfile (form-param request "file")))

(defn do-upload
  "Handles a file upload.

   Request Parameters:
     user - form-data field containing a username.
     file - form-data field containing file data.
     dest - form-data field containing the destination path in iRODS."
  [request]
  (log/debug "do-upload")
  (let [dest-path (form-param request "dest")
        user (form-param request "user")
        input-file (multipart-inputfile request)]
    (if (not (super-user? user))
      (create-response (irods-actions/load-from-file user dest-path input-file) "text/html")
      (create-response
        {:action "upload"
         :status "failure"
         :error_code ERR_NOT_AUTHORIZED
         :reason "action not allowed by user"
         :user user}
        "text/html"))))

(defn attachment?
  [request]
  (if (not (query-param? request "attachment"))
    true
    (let [disp (query-param request "attachment")]
      (if (= "1" disp)
        true
        false))))

(defn do-download
  "Handles a file download

   Request Parameters:
     user - Query string field containing a username.
     path - Query string field containing the path to download."
  [request]
  (log/debug "do-download")  
  (cond
    (not (query-param? request "user")) (bad-query "user" "download")
    (not (query-param? request "path")) (bad-query "path" "download")
    :else
    (let [user (query-param request "user")
          path (query-param request "path")]
      (log/info (str "User for download: " user))
      (log/info (str "Path to download: " path))
      (cond
        (super-user? user)
        (create-response {:action "download"
                          :status "failure"
                          :reason "action not allowed by that user"
                          :user user})
        
        ;;; If disable is not included, assume the attachment
        ;;; part should be left out.
        (not (query-param? request "attachment"))
        (rsp-utils/header
          (irods-actions/download-file user path)
          "Content-Disposition"
          (str "attachment; filename=\"" (utils/basename path) "\""))
        
        (not (attachment? request))
        (rsp-utils/header
          (irods-actions/download-file user path)
          "Content-Disposition"
          (str "filename=\"" (utils/basename path) "\""))
        
        :else
        (rsp-utils/header
          (irods-actions/download-file user path)
          "Content-Disposition"
          (str "attachment; filename=\"" (utils/basename path) "\""))))))

(defn do-preview
  "Handles a file preview.

   Request Parameters:
     user - Query string field containing a username.
     path - Query string field containing the file to preview."
  [request]
  (log/debug "do-preview")
  (cond
    (not (query-param? request "user"))
    (bad-query "user" "preview")
    
    (not (query-param? request "path"))
    (bad-query "path" "preview")
    
    :else
    (let [user (query-param request "user")
          path (query-param request "path")]
      (if (not (super-user? user))
        (create-response
          {:action "preview"
           :preview (irods-actions/preview user path preview-size)})
        (create-response
          {:action "preview"
           :status "failure"
           :error_code ERR_NOT_AUTHORIZED
           :reason "action not allowed by that user"
           :user user
           :path path})))))

(defn do-exists
  "Returns True if the path exists and False if it doesn't."
  [request]
  (log/debug "do-exists")
  (cond
    (not (query-param? request "user"))
    (bad-query "user" "exists")
    
    (not (valid-body? request {:paths vector?}))
    (create-response (bad-body request {:paths vector?}))
    
    :else
    (let [paths (:paths (:body request))]
      (create-response
        {:action "exists"
         :status "success"
         :paths  (apply conj {} (map (fn [p] {p (irods-actions/path-exists? p)}) paths))}))))

(defn do-manifest
  "Returns a manifest consisting of preview and rawcontent fields for a file."
  [request]
  (log/debug "do-manifest")
  (cond
    (not (query-param? request "user")) (bad-query "user" "manifest")
    (not (query-param? request "path")) (bad-query "path" "manifest")
    :else
    (let [user (query-param request "user")
          path (query-param request "path")]
      (create-response (irods-actions/manifest user path data-threshold)))))

(defroutes nibblonian-routes
  (GET "/" [] "Welcome to Nibblonian!")
  
  (GET "/home" request
       (retry do-homedir request))
  
  (POST "/exists" request
       (retry do-exists request))
  
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
  
  (POST "/file/urlupload" request
        (retry do-urlupload request))
  
  (POST "/file/upload" request
        (retry do-upload request))
  
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