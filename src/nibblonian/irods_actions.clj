(ns nibblonian.irods-actions
  (:require [clojure.tools.logging :as log]
            [clojure.java.io :as ds]
            [nibblonian.ssl :as ssl]
            [ring.util.codec :as cdc]
            [clojure.data.json :as json]
            [clojure.string :as string])
  (:use [nibblonian.jargon]
        [clojure-commons.file-utils]
        [nibblonian.error-codes]))

(defn filter-unreadable
  [metadata-list]
   (into [] (filter
              (fn [m]
                (:read (:permissions m)))
              metadata-list)))

(defn list-dir
  "A non-recursive listing of a directory. Contains entries for files.

   The map for the directory listing looks like this:
     {:id \"full path to the top-level directory\"
      :label \"basename of the path\"
      :files A sequence of file maps
      :folders A sequence of directory maps}

   Parameters:
     user - String containing the username of the user requesting the listing.
     path - String containing path to the top-level directory in iRODS.

   Returns:
     A tree of maps as described above."
  ([user path]
     (list-dir user path true))
  
  ([user path include-files]
     (log/debug (str "list-dir " user " " path))
     (with-jargon
       (cond 
         (not (user-exists? user))
         {:action "list-dir" 
          :status "failure" 
          :error_code ERR_NOT_A_USER 
          :reason "user does not exist"
          :user user}
         
         (not (is-readable? user path))
         {:action "list-dir"
          :status "failure"
          :error_code ERR_NOT_READABLE
          :reason "is not readable"
          :path path
          :user user}
         
         :else
         (let [data   (list-all (rm-last-slash path))
               groups (group-by (fn [datum] (. datum isCollection)) data)
               files  (get groups false)
               dirs   (get groups true)
               retval (if include-files
                        {:id path
                         :label         (basename path)
                         :hasSubDirs    (> (count dirs) 0)
                         :date-created  (created-date path)
                         :date-modified (lastmod-date path)
                         :permissions   (collection-perm-map user path)
                         :files         (filter-unreadable (map (fn [f] (sub-file-maps user f)) files)) 
                         :folders       (filter-unreadable (map (fn [d] (sub-dir-maps user d)) dirs))}
                        {:id path
                         :date-created  (created-date path)
                         :date-modified (lastmod-date path)
                         :permissions   (collection-perm-map user path)
                         :hasSubDirs    (> (count dirs) 0)
                         :label         (basename path)
                         :folders       (filter-unreadable (map (fn [d] (sub-dir-maps user d)) dirs))})]
           retval)))))

(defn create
  "Creates a directory at 'path' in iRODS and sets the user to 'user'.

   Parameters:
     user - String containing the username of the user requesting the directory.
     path - The path that the directory will be created at in iRODS.

   Returns a map of the format {:action \"create\" :path \"path\"}"
  [user path]
  (log/debug (str "create " user " " path))
  (with-jargon
    (cond
      (not (collection-writeable? user (dirname path)))
      {:action "create"
       :path path
       :status "failure"
       :error_code ERR_NOT_WRITEABLE
       :reason "parent directory not writeable"}
      
      (exists? path)
      {:action "create" 
       :path path 
       :status "failure"
       :error_code ERR_EXISTS
       :reason "already exists"}
      
      :else
      (do 
        (mkdir path)
        (set-owner path user)
        {:action "create" 
         :path path 
         :status "success" 
         :permissions (collection-perm-map user path)}))))

(defn- del
  "Performs some validation and calls delete.

   Parameters:
     user - username of the user requesting the directory deletion.
     paths - a sequence of strings containing directory paths.

   Returns a map describing the success or failure of the deletion command."
  [user paths type-func? action type-error]
  (with-jargon
    (cond
      ;Make sure all of the paths exist.
      (not (paths-exist? paths))
      {:action action
       :reason "does not exist" 
       :paths (for [path paths :when (not (exists? path))] path)
       :status "failure"
       :error_code ERR_DOES_NOT_EXIST} 
      
      ;Make sure all of the paths are writeable.
      (not (paths-writeable? user paths))
      {:action action
       :reason "is not writeable" 
       :paths (for [path paths :when (not (is-writeable? user path))] path) 
       :status "failure"
       :error_code ERR_NOT_WRITEABLE}
      
      ;Make sure all of the paths are directories.
      (not (reduce (fn [f s] (and f s)) (map (fn [p] (type-func? p)) paths)))
      {:action action
       :status "failure"
       :error_code type-error
       :reason "is not a dir"
       :paths (for [p paths :when (not (type-func? p))] p)}
      
      :else
      (let [results (map delete paths)]
        (if (== 0 (count results))
          {:action action :status "success" :paths paths}
          {:action action 
           :status "failure"
           :error_code ERR_INCOMPLETE_DELETION
           :paths results})))))

(defn delete-dirs
  [user paths]
  (del user paths is-dir? "delete-dirs" ERR_NOT_A_FOLDER))

(defn delete-files
  [user paths]
  (del user paths is-file? "delete-files" ERR_NOT_A_FILE))

(defn- mv
  "Moves directories listed in 'sources' into the directory listed in 'dest'. This
   works by calling move and passing it move-dir."
  [user sources dest type-func? action type-error]
  (with-jargon
    (let [path-list  (conj sources dest)
          dest-paths (into [] (map (fn [s] (path-join dest (basename s))) sources))
          types?      (reduce 
                       (fn [f s] (and f s))
                       (map (fn [d] (type-func? d)) path-list))]
      (cond
        ;Make sure that all source paths in the request actually exist.
        (not (paths-exist? sources))
        {:action action
         :status "failure" 
         :error_code ERR_DOES_NOT_EXIST 
         :reason "does not exist" 
         :paths (for [path sources :when (not (exists? path))] path)}
        
        ;Make sure that the destination path actually exists.
        (not (exists? dest))
        {:action action 
         :status "failure" 
         :error_code ERR_DOES_NOT_EXIST 
         :reason "does not exist" 
         :paths [dest]}
        
        ;Make sure all the paths in the request are writeable.
        (not (paths-writeable? user path-list))
        {:action action 
         :status "failure" 
         :error_code ERR_NOT_WRITEABLE 
         :reason "is not writeable" 
         :paths (for [path path-list :when (not (is-writeable? user path))] path)}
        
        ;Make sure that the destination directories don't exist already.
        (not (every? false? (map exists? dest-paths)))
        {:action action 
         :status "failure" 
         :reason "exists"
         :error_code ERR_EXISTS
         :paths (filter (fn [p] (exists? p)) dest-paths)}
        
        ;Make sure that everything is a directory.
        (not types?)
        {:action action
         :status "failure"
         :paths (for [path path-list :when (not (type-func? path))]
                  {:reason "is not a directory" 
                   :path path 
                   :error_code type-error})}
        
        :else
        (let [results (move-all sources dest)]
          (if (== 0 (count results))
            {:action action :status "success" :sources sources :dest dest}
            {:action action 
             :status "failure"
             :error_code ERR_INCOMPLETE_MOVE
             :paths results}))))))

(defn move-directories
  [user sources dest]
  (mv user sources dest is-dir? "move-dirs" ERR_NOT_A_FOLDER))

(defn move-files
  [user sources dest]
  (mv user sources dest is-file? "move-files" ERR_NOT_A_FILE))

(defn- rname
  "High-level file renaming. Calls rename-func, passing it file-rename as the mv-func param."
  [user source dest type-func? action type-error]
  (with-jargon
    (cond
      (not (exists? source))
      {:action action
       :reason "exists" 
       :path source 
       :status "failure"
       :error_code ERR_DOES_NOT_EXIST}
      
      (not (is-writeable? user source))
      {:action action
       :status "failure" 
       :reason "not writeable"
       :error_code ERR_NOT_WRITEABLE
       :path source}
      
      (not (type-func? source))
      {:action action 
       :status "failure" 
       :paths source
       :error_code ERR_NOT_A_FILE
       :reason "is not a file."}
      
      (exists? dest)
      {:action action 
       :status "failure" 
       :error_code ERR_EXISTS
       :reason "exists" 
       :path dest}
      
      :else
      (let [result (move source dest)]
        (if (nil? result)
          {:action action :status "success" :source source :dest dest :user user}
          {:action action 
           :status "failure"
           :error_code ERR_INCOMPLETE_RENAME
           :paths result
           :user user})))))

(defn rename-file
  [user source dest]
  (rname user source dest is-file? "rename-file" ERR_NOT_A_FILE))

(defn rename-directory
  [user source dest]
  (rname user source dest is-dir? "rename-directory" ERR_NOT_A_FOLDER))

(defn download-file
  "Returns a response map filled out with info that lets the client download
   a file."
  [user file-path]
  (with-jargon
    (cond
      (not (exists? file-path))
      {:status 404 
       :body (str "File " file-path " does not exist.")}
      
      (is-readable? user file-path)
      {:status 200 
       :body (input-stream file-path)}
      
      :else
      {:status 400 
       :body (str "File " file-path " isn't writeable.")})))

(defn preview
  "Grabs a preview of a file in iRODS.

   Parameters:
     user - The username of the user requesting the preview.
     path - The path to the file in iRODS that will be previewed.
     size - The size (in bytes) of the preview to be created."
  [user path size]
  (with-jargon
    (log/debug (str "preview " user " " path " " size))
    (cond
      (not (exists? path))
      {:action "preview" 
       :status "failed" 
       :reason "Does not exist."
       :error_code ERR_DOES_NOT_EXIST
       :path path}
      
      (not (is-readable? user path))
      {:action "preview" 
       :status "failed" 
       :reason "Is not readable." 
       :path path
       :error_code ERR_NOT_READABLE}
      
      (not (is-file? path))
      {:action "preview" 
       :status "failure" 
       :reason "isn't a file"
       :error_code ERR_NOT_A_FILE
       :path path 
       :user user}
      
      :else
      (.
        (let [realsize (file-size path)
              buffsize (if (<= realsize size) realsize size)
              buff (char-array buffsize)]
          (do
            (read-file path buff)
            (. (StringBuilder.) append buff)))
        toString))))

(defn user-home-dir
  [staging-dir user set-owner?]
  "Returns the path to the user's home directory in our zone of iRODS.

    Parameters:
      user - String containing a username

    Returns:
      A string containing the absolute path of the user's home directory."
  (with-jargon
    (let [user-home (path-join staging-dir user)]
      (if (not (exists? user-home))
        (. (file user-home) mkdirs))
      user-home)))

(defn fail-resp
  [action status reason error-code]
  {:action action :status status :reason reason :error_code error-code})

(defn metadata-get
  [user path]
  (with-jargon
    (cond
      (not (exists? path))
      (fail-resp "get-metadata" "failure" "Path doesn't exist." ERR_DOES_NOT_EXIST)
      
      (not (is-readable? user path))
      (fail-resp "get-metadata" "failure" "Path isn't readable by user" ERR_NOT_READABLE)
      
      :else
      {:action "get-metadata"
       :status "success"
       :metadata (get-metadata path)})))

(defn get-tree
  [user path]
  (with-jargon
    (cond
      (not (exists? path))
      (fail-resp "get-tree-urls" "failure" "Path doesn't exist." ERR_DOES_NOT_EXIST)
      
      (not (is-readable? user path))
      (fail-resp "get-tree-urls" "failure" "Path isn't readable by user" ERR_NOT_READABLE)
      
      :else
      (let [value (:value (first (get-attribute path "tree-urls")))]
        (log/warn value)
        (json/read-json value)))))

(defn metadata-set
  [user path avu-map]
  (with-jargon
    (cond
      (= "failure" (:status avu-map))
      (fail-resp "set-metadata" "failure" "Bad JSON." ERR_INVALID_JSON)
      
      (not (exists? path))
      (fail-resp "set-metadata" "failure" "Path doesn't exist." ERR_DOES_NOT_EXIST)
      
      (not (is-writeable? user path))
      (fail-resp "set-metadata" "failure" "Path isn't writeable by user." ERR_NOT_WRITEABLE)
      
      :else
      (do
        (set-metadata path (:attr avu-map) (:value avu-map) (:unit avu-map))
        {:action "set-metadata" :status "success" :path path :user user}))))

(defn set-tree
  [user path tree-urls]
  (with-jargon
    (cond
      (not (exists? path))
      (fail-resp "set-tree-urls" "failure" "Path doesn't exist." ERR_DOES_NOT_EXIST)
      
      (not (is-writeable? user path))
      (fail-resp "set-tree-urls" "failure" "Path isn't writeable by user." ERR_NOT_WRITEABLE)
      
      :else
      (let [tree-urls (:tree-urls tree-urls)
            curr-val  (if (attribute? meta path "tree-urls")
                        (json/read-json (:value (first (get-attribute path "tree-urls"))))
                        [])
            new-val (json/json-str (flatten (conj curr-val tree-urls)))]
        (set-metadata path "tree-urls" new-val "")
        {:action "set-tree-urls" :status "success" :path path :user user}))))

(defn metadata-delete
  [user path attr]
  (with-jargon
    (cond
     (not (exists? path))
     (fail-resp "delete-metadata" "failure" "Path doesn't exist." ERR_DOES_NOT_EXIST)
     
     (not (is-writeable? user path))
     (fail-resp "delete-metadata" "failure" "Path isn't writeable by user." ERR_NOT_WRITEABLE)
     
     :else
     (do
       (delete-metadata path attr)
       {:action "delete-metadata" :status "success" :path path :user user}))))

(defn path-exists?
  [path]
  (with-jargon (exists? path)))

(defn- format-tree-urls
  [treeurl-maps]
  (if (> (count treeurl-maps) 0)
    (json/read-json (:value (first (seq treeurl-maps))))
    []))

(defn tail
  [num-chars tail-str]
  (if (< (count tail-str) num-chars)
    tail-str
    (. tail-str substring (- (count tail-str) num-chars))))

(defn extension?
  [path ext]
  (=
   (string/lower-case ext)
   (string/lower-case (tail (count ext) path))))

(defn manifest
  [user path data-threshold]
  (with-jargon
    (cond
      (not (exists? path))
      (fail-resp "manifest" "failure" "path doesn't exist." ERR_DOES_NOT_EXIST)
      
      (not (is-file? path))
      (fail-resp "manifest" "failure" "path isn't a file." ERR_NOT_A_FILE)
      
      (not (is-readable? user path))
      (fail-resp "manifest" "failure" "path isn't readable." ERR_NOT_READABLE)
      
      :else
      (let [manifest         {:action "manifest"
                              :tree-urls (format-tree-urls (get-attribute path "tree-urls"))}
            file-size        (file-size path)
            preview-path     (str "file/preview?user=" (cdc/url-encode user) "&path=" (cdc/url-encode path))
            rawcontents-path (str "file/download?user=" (cdc/url-encode user) "&path=" (cdc/url-encode path))
            rc-no-disp       (str rawcontents-path "&attachment=0")]
        (cond
          (extension? path ".png")
          (merge manifest {:png rawcontents-path})
          
          (extension? path ".pdf")
          (merge manifest {:pdf rc-no-disp})
          
          (>= file-size data-threshold)
          (merge manifest {:preview preview-path})
          
          :else
          (merge manifest {:rawcontents rawcontents-path}))))))
