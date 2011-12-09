(ns nibblonian.irods-actions
  (:require [clojure.tools.logging :as log]
            [clojure.java.io :as ds]
            [nibblonian.ssl :as ssl]
            [ring.util.codec :as cdc]
            [clojure.data.json :as json]
            [clojure.string :as string])
  (:use [nibblonian.irods-base]
        [nibblonian.irods-records]
        [nibblonian.utils]
        [nibblonian.error-codes])
  (:import [java.io FileInputStream]
           [org.irods.jargon.core.pub.io IRODSFileReader]))

(defn clean-return
  [fileSystem retval]
  (. fileSystem close)
  retval)

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
     (let [jargon-records (create-records)
           context        (:context jargon-records)
           jargon         (:jargon jargon-records)
           lister         (:lister context)
           fileSystem     (:fileSystem context)]
       (cond 
         (not (user-exists? jargon user))
         (clean-return 
           fileSystem 
           {:action "list-dir"
            :status "failure"
            :error_code ERR_NOT_A_USER
            :reason "user does not exist"
            :user user})
         
         (not (is-readable? jargon user path))
         (clean-return 
           fileSystem 
           {:action "list-dir"
            :status "failure"
            :error_code ERR_NOT_READABLE
            :reason "is not readable"
            :path path
            :user user})
         
         :else
         (let [data   (. lister listDataObjectsAndCollectionsUnderPath (rm-last-slash path))
               groups (group-by (fn [datum] (. datum isCollection)) data)
               files  (get groups false)
               dirs   (get groups true)
               retval (if include-files
                        {:id path
                         :label         (basename path)
                         :hasSubDirs    (> (count dirs) 0)
                         :date-created  (created-date jargon path)
                         :date-modified (lastmod-date jargon path)
                         :permissions   (collection-perm-map jargon user path)
                         :files         (filter-unreadable (map (fn [f] (sub-file-maps jargon user f)) files)) 
                         :folders       (filter-unreadable (map (fn [d] (sub-dir-maps jargon user d)) dirs))}
                        {:id path
                         :date-created  (created-date jargon path)
                         :date-modified (lastmod-date jargon path)
                         :permissions   (collection-perm-map jargon user path)
                         :hasSubDirs    (> (count dirs) 0)
                         :label         (basename path)
                         :folders       (filter-unreadable (map (fn [d] (sub-dir-maps jargon user d)) dirs))})]
           (clean-return fileSystem retval))))))

(defn create
  "Creates a directory at 'path' in iRODS and sets the user to 'user'.

   Parameters:
     user - String containing the username of the user requesting the directory.
     path - The path that the directory will be created at in iRODS.

   Returns a map of the format {:action \"create\" :path \"path\"}"
  [user path]
  (log/debug (str "create " user " " path))
  (let [jargon-records (create-records)
        context        (:context jargon-records)
        jargon         (:jargon jargon-records)
        fileSystem     (:fileSystem context)
        fileSystemAO   (:fileSystemAO context)]
    (cond
      (not (collection-writeable? jargon user (dirname path)))
      (clean-return
        fileSystem
        {:action "create"
         :path path
         :status "failure"
         :error_code ERR_NOT_WRITEABLE
         :reason "parent directory not writeable"})
      
      (exists? jargon path)
      (clean-return 
        fileSystem 
        {:action "create" 
         :path path 
         :status "failure"
         :error_code ERR_EXISTS
         :reason "already exists"})
      
      :else
      (let [ifile (file jargon path)]
        (. fileSystemAO mkdir ifile true)
        (set-owner jargon path user)
        (clean-return 
          fileSystem 
          {:action "create" 
           :path path 
           :status "success" 
           :permissions (collection-perm-map jargon user path)})))))

(defn- delete-dir
  "Deletes a directory in iRODS.

   Parameters:
     user - A string containing the username of a user.
     path - A string containing the path to the directory being deleted."
  [user path fileSystemAO jargon]
  (log/debug (str "delete-dir " user " " path))
  (. fileSystemAO directoryDeleteForce (file jargon path)))

(defn- delete-file
  "Deletes a file in iRODS.

   Parameters:
     user - A string containing the username of a user.
     path - A string containing the path to the file being deleted."
  [user path fileSystemAO jargon]
  (log/debug (str "delete-file " user " " path))
  (. fileSystemAO fileDeleteForce (file jargon path)))

(defn- delete
  "Calls 'delete-func' and performs error-checking for a deletion command.

   Parameters:
     user - A string containing the username of a user.
     paths - A sequence of strings containing paths to be deleted.
     delete-func - Either delete-dir or delete-file.

   Returns a map describing the success or failure of the deletion attempt."
  [user paths delete-func fileSystemAO jargon]
  (log/debug (str "delete " user " " paths " " delete-func))
  (cond
    (not (paths-exist? jargon paths))
    (for [path paths :when (not (exists? jargon path))]
      {:reason "does not exist" :path path :error_code ERR_DOES_NOT_EXIST})
    
    (not (paths-writeable? jargon user paths))
    (for [path paths :when (not (is-writeable? jargon user path))]
        {:reason "is not writeable" :paths path :error_code ERR_NOT_WRITEABLE})
    
    :else
    (filter 
      (fn [p] 
        (not (nil? p))) 
      (map 
        (fn [f] (delete-func user f fileSystemAO jargon)) 
        paths))))

(defn delete-dirs
  "Performs some validation and calls delete.

   Parameters:
     user - username of the user requesting the directory deletion.
     paths - a sequence of strings containing directory paths.

   Returns a map describing the success or failure of the deletion command."
  [user paths]
  (let [jargon-records (create-records)
        context        (:context jargon-records)
        jargon         (:jargon jargon-records)
        fileSystem     (:fileSystem context)
        fileSystemAO   (:fileSystemAO context)]
    (log/debug (str "delete-dirs " user " " paths))
    (if (reduce (fn [f s] (and f s)) (map (fn [p] (is-dir? jargon p)) paths))
      (let [results (delete user paths delete-dir fileSystemAO jargon)]
        (if (== 0 (count results))
          (clean-return 
            fileSystem 
            {:action "delete-dirs" 
             :status "success" 
             :paths paths})
          (clean-return 
            fileSystem 
            {:action "delete-dirs" 
             :status "failure"
             :error_code ERR_INCOMPLETE_DELETION
             :paths results})))
      (clean-return 
        fileSystem 
        (merge
          {:action "delete-dirs"
           :status "failure"
           :error_code ERR_NOT_A_FOLDER
           :reason "is not a dir"
           :paths  (for [p paths :when (not (is-dir? jargon p))] p)})))))

(defn delete-files
  "Performs some validation and calls delete.

   Parameters:
     user - username of the user requesting the file deletions.
     paths - a sequence of strings containing the file paths.

   Returns a map describing the success or failure of the deletion command."
  [user paths]
  (let [jargon-records (create-records)
        context        (:context jargon-records)
        jargon         (:jargon jargon-records)
        fileSystem     (:fileSystem context)
        fileSystemAO   (:fileSystemAO context)]
    (log/debug (str "delete-files " user " " paths))
    (if (reduce (fn [f s] (and f s)) (map (fn [p] (is-file? jargon p)) paths))
      (let [results (delete user paths delete-file fileSystemAO jargon)]
        (if (== 0 (count results))
          (clean-return fileSystem {:action "delete-files" :status "success" :paths paths})
          (clean-return 
            fileSystem 
            {:action "delete-files" 
             :status "failure"
             :error_code ERR_INCOMPLETE_DELETION
             :paths results})))
      (let [retval (merge {:action "delete-files"
                           :status "failure"
                           :error_code ERR_NOT_A_FILE
                           :reason "is not a file"
                           :paths
                           (for [p paths :when (not (is-file? jargon p))] p)})]
        (clean-return fileSystem retval)))))

(defn- move-dir
  "Moves the 'source' directory into the 'dest' directory."
  [user source dest fileSystemAO jargon]
  (log/debug (str "move-dir " user " " source " " dest))
  (. fileSystemAO renameDirectory (file jargon source) (file jargon (dest-path source dest))))

(defn- move-file
  "Moves the 'source' file into the 'dest' directory."
  [user source dest fileSystemAO jargon]
  (log/debug (str "move-file " user " " source " " dest))
  (. fileSystemAO renameFile (file jargon source) (file jargon (dest-path source dest))))

(defn- dir-rename
  "Renames 'source' directory into 'dest'."
  [user source dest fileSystemAO jargon]
  (log/debug (str "dir-rename " user " " source " " dest))
  (. fileSystemAO renameDirectory (file jargon source) (file jargon dest)))

(defn- file-rename
  "Renames 'source' file into 'dest'."
  [user source dest fileSystemAO jargon]
  (log/debug (str "file-rename " user " " source " " dest))
  (. fileSystemAO renameFile (file jargon source) (file jargon dest)))

(defn- move
  "Performs error checking and calls mv-func on the 'paths' in the 'sources' sequence."
  [user sources dest mv-func fileSystemAO jargon]
  (log/debug (str "move " user " " sources " " dest " " mv-func))
  (filter 
    (fn [p] (not (nil? p))) 
    (map 
      (fn [f] (mv-func user f dest fileSystemAO jargon)) 
      sources)))

(defn move-directories
  "Moves directories listed in 'sources' into the directory listed in 'dest'. This
   works by calling move and passing it move-dir."
  [user sources dest]
  (let [jargon-records (create-records)
        context        (:context jargon-records)
        jargon         (:jargon jargon-records)
        fileSystem     (:fileSystem context)
        fileSystemAO   (:fileSystemAO context)
        path-list      (conj sources dest)
        dest-paths     (into [] (map (fn [s] (path-join dest (basename s))) sources))
        dirs?          (reduce 
                         (fn [f s] (and f s))
                         (map (fn [d] (is-dir? jargon d)) path-list))]
    (log/debug (str "move-directories " user " " sources " " dest))
    (cond
      ;Make sure that all paths in the request actually exist.
      (not (paths-exist? jargon path-list))
      {:action "move-dirs" 
       :status "failure" 
       :error_code ERR_DOES_NOT_EXIST 
       :reason "does not exist" 
       :paths (for [path path-list :when (not (exists? jargon path))] path)}
      
      ;Make sure all the paths in the request are writeable.
      (not (paths-writeable? jargon user path-list))
      {:action "move-dirs" 
       :status "failure" 
       :error_code ERR_NOT_WRITEABLE 
       :reason "is not writeable" 
       :paths (for [path path-list :when (not (is-writeable? jargon user path))] path)}
      
      ;Make sure that the destination directories don't exist already.
      (not (every? false? (into [] (map (fn [d] (exists? jargon d)) dest-paths))))
      {:action "move-dirs" 
       :status "failure" 
       :reason "exists"
       :error_code ERR_EXISTS
       :paths (filter (fn [p] (exists? jargon p)) dest-paths)}
      
      ;Make sure that everything is a directory.
      (not dirs?)
      (clean-return 
        fileSystem 
        {:action "move-dirs"
         :status "failure"
         :paths (for [path path-list :when (not (is-dir? jargon path))]
                  {:reason "is not a directory" 
                   :path path 
                   :error_code ERR_NOT_A_FOLDER})})
      
      :else
      (let [results (move user sources dest move-dir fileSystemAO jargon)]
        (if (== 0 (count results))
          (clean-return 
            fileSystem 
            {:action "move-dirs" 
             :status "success" 
             :sources sources 
             :dest dest})
          (clean-return 
            fileSystem 
            {:action "move-dirs" 
             :status "failure"
             :error_code ERR_INCOMPLETE_MOVE
             :paths results}))))))

(defn move-files
  "Moves files listed in 'sources' into the directory listed in 'dest'. This works
   by calling move and passing it move-file."
  [user sources dest]
  (let [jargon-records (create-records)
        context        (:context jargon-records)
        jargon         (:jargon jargon-records)
        fileSystem     (:fileSystem context)
        fileSystemAO   (:fileSystemAO context)
        path-list      (conj sources dest)
        dest-paths     (into [] (map (fn [s] (path-join dest (basename s))) sources))
        files?         (reduce 
                         (fn [f s] (and f s)) 
                         (map (fn [s] (is-file? jargon s)) sources))]
    (log/debug (str "move-files " user " " sources " " dest))
    (cond
      ;Make sure that all paths in the request actually exist.
      (not (paths-exist? jargon path-list))
      {:action "move-files" 
       :status "failure" 
       :reason "does not exist"
       :error_code ERR_DOES_NOT_EXIST
       :paths (for [path path-list :when (not (exists? jargon path))] path)}
      
      ;Make sure all the paths in the request are writeable.
      (not (paths-writeable? jargon user path-list))
      {:action "move-files"
       :status "failure" 
       :reason "is not writeable"
       :error_code ERR_NOT_WRITEABLE
       :paths (for [path path-list :when (not (is-writeable? jargon user path))] path)}
      
      ;Make sure the destination is actually a directory.
      (not (is-dir? jargon dest))
      (clean-return 
        fileSystem 
        {:action "move-file"
         :status "failure"
         :error_code ERR_NOT_A_FOLDER
         :path dest
         :reason "is not a directory"})
      
      ;Make sure that the destination directories don't exist already.
      (not (every? false? (into [] (map (fn [d] (exists? jargon d)) dest-paths))))
      {:action "move-files" 
       :status "failure"
       :error_code ERR_EXISTS
       :reason "exists" 
       :paths (filter (fn [p] (exists? jargon p)) dest-paths)}
      
      ;Make sure the sources are actually files.
      (not files?)
      (clean-return 
        fileSystem 
        {:action "move-files"
         :status "failure"
         :paths (for [path sources :when (not (is-file? jargon path))]
                  {:reason "is not a file" 
                   :path path
                   :error_code ERR_NOT_A_FILE})})
      
      :else
      (let [results (move user sources dest move-file fileSystemAO jargon)]
        (if (== 0 (count results))
          (clean-return 
            fileSystem 
            {:action "move-files"
             :status "success"
             :sources sources
             :dest dest
             :results results})
          (clean-return 
            fileSystem 
            {:action "move-files"
             :status "failure"
             :error_code ERR_INCOMPLETE_MOVE
             :paths results}))))))

(defn- rename-func
  "Renames source to dest using mv-func."
  [user source dest mv-func fileSystemAO jargon]
  (log/debug (str "rename-func " user " " source " " dest " " mv-func))
  (filter
    (fn [p] (not (nil? p)))
    (map
      (fn [f] (mv-func user f dest fileSystemAO jargon))
      [source])))

(defn rename-file
  "High-level file renaming. Calls rename-func, passing it file-rename as the mv-func param."
  [user source dest]
  (let [jargon-records (create-records)
        context        (:context jargon-records)
        jargon         (:jargon jargon-records)
        fileSystem     (:fileSystem context)
        fileSystemAO   (:fileSystemAO context)]
    (log/debug (str "rename-file " user " " source " " dest))
    (cond
      (not (exists? jargon source))
      {:reason "exists" 
       :path source 
       :status "failure"
       :error_code ERR_DOES_NOT_EXIST}
      
      (not (is-writeable? jargon user source))
      {:action "rename-file" 
       :status "failure" 
       :reason "not writeable"
       :error_code ERR_NOT_WRITEABLE
       :path source}
      
      (not (is-file? jargon source))
      (clean-return 
        fileSystem 
        {:action "rename-file" 
         :status "failure" 
         :paths source
         :error_code ERR_NOT_A_FILE
         :reason "is not a file."})
      
      (exists? jargon dest)
      {:action "rename-file" 
       :status "failure" 
       :error_code ERR_EXISTS
       :reason "exists" 
       :path dest}
      
      :else
      (let [results (rename-func user source dest file-rename fileSystemAO jargon)]
        (if (== 0 (count results))
          (clean-return fileSystem {:action "rename-file" :status "success" :source source :dest dest :user user})
          (clean-return 
            fileSystem 
            {:action "rename-file" 
             :status "failure"
             :error_code ERR_INCOMPLETE_RENAME
             :paths results 
             :user user}))))))

(defn rename-directory
  "High level dir renaming. Calls rename-func, passing it dir-rename as the mv-func param."
  [user source dest]
  (let [jargon-records (create-records)
        context        (:context jargon-records)
        jargon         (:jargon jargon-records)
        fileSystem     (:fileSystem context)
        fileSystemAO   (:fileSystemAO context)]
    (log/debug (str "rename-directory " user " " source " " dest))
    (cond
      (not (exists? jargon source))
      {:action "rename-directory" 
       :status "failure" 
       :reason "does not exist"
       :error_code ERR_DOES_NOT_EXIST
       :path source}
      
      (not (is-writeable? jargon user source))
      {:action "rename-directory" 
       :status "failure" 
       :reason "not writeable"
       :error_code ERR_NOT_WRITEABLE
       :path source}
      
      (not (is-dir? jargon source))
      (clean-return 
        fileSystem 
        {:action "rename-directory"
         :status "failure"
         :error_code ERR_NOT_A_FOLDER
         :paths source
         :reason "is not a directory"})
      
      (exists? jargon dest)
      {:action "rename-directory" 
       :status "failure" 
       :reason "exists"
       :error_code ERR_EXISTS
       :path dest}
      
      :else
      (let [results (rename-func user source dest dir-rename fileSystemAO jargon)]
        (if (== 0 (count results))
          (clean-return 
            fileSystem 
            {:action "rename-directory"
             :status "success"
             :source source
             :dest dest
             :user user})
          (clean-return 
            fileSystem 
            {:action "rename-directory"
             :status "failure"
             :error_code ERR_INCOMPLETE_RENAME
             :paths results
             :user user}))))))

(defn- output-stream
  "Returns an FileOutputStream for a file in iRODS pointed to by 'output-path'."
  [output-path fileFactory jargon]
  (log/debug (str "output-stream " output-path))
  (. fileFactory instanceIRODSFileOutputStream (file jargon output-path)))

(defn- input-stream
  "Returns a FileInputStream for a file in iRODS pointed to by 'input-path'"
  [input-path fileFactory jargon]
  (log/debug (str "input-stream " input-path))
  (. fileFactory instanceIRODSFileInputStream (file jargon input-path)))

(defn download-file
  "Returns a response map filled out with info that lets the client download
   a file."
  [user file-path]
  (let [jargon-records (create-records)
        context        (:context jargon-records)
        jargon         (:jargon jargon-records)
        fileSystem     (:fileSystem context)
        fileFactory    (:fileFactory context)]
    (cond
      (not (exists? jargon file-path))
      (clean-return 
        fileSystem 
        {:status 404 
         :body (str "File " file-path " does not exist.")})
      
      (is-readable? jargon user file-path)
      {:status 200 :body (input-stream file-path fileFactory jargon)}
      
      :else
      (clean-return 
        fileSystem 
        {:status 400 
         :body (str "File " file-path " isn't writeable.")}))))

(defn load-from-stream
  "Dumps the contents of in-stream (an InputStream) into the file represented by the
   joining of dest-dir and file-name. Makes sure the file is owned by user."
  [user dest-dir file-name in-stream threaded fileFactory jargon fileSystem]
  (log/debug (str "load-from-stream " user " " dest-dir " " file-name " " in-stream))
  (cond
    (not (is-dir? jargon dest-dir))
    {:status "failure" :reason "Is not a directory." :id dest-dir}
    
    (not (is-writeable? jargon user dest-dir))
    {:status "failure" :reason "not writeable" :id dest-dir}
    
    threaded
    (do 
      (future
        (let [out              (output-stream (path-join dest-dir file-name) fileFactory jargon)
              destination-path (dest-path dest-dir file-name)]
          (ds/copy in-stream out)
          (. in-stream close)
          (. out close)
          (set-owner jargon destination-path user)
          (. fileSystem close)))
      {:status "success" 
       :id (path-join dest-dir file-name)
       :permissions (dataobject-perm-map jargon user (path-join dest-dir file-name))})
    
    :else
    (let [out (output-stream (path-join dest-dir file-name) fileFactory jargon)
          destination-path (path-join dest-dir file-name)]
      (ds/copy in-stream out)
      (. in-stream close)
      (. out close)
      (set-owner jargon destination-path user)
      {:status "success" 
       :id destination-path 
       :permissions (dataobject-perm-map jargon user (path-join dest-dir file-name))})))

(defn load-from-file
  "Loads the contents of the file at in-file into dest-path in iRODS.

   Parameters:
     user - Username of the user requesting the load.
     dest-path - String containing the destination file-path in iRODS.
     in-file - String containing the local file-path of the file to be loaded."
  [user dest-path in-file]
  (let [jargon-records (create-records)
        context        (:context jargon-records)
        jargon         (:jargon jargon-records)
        fileSystem     (:fileSystem context)
        fileFactory    (:fileFactory context)]
    (log/debug (str "load-from-file " user " " dest-path " " in-file))
    (let [dest-dir  (dirname dest-path)
          file-name (basename dest-path)
          in-stream (FileInputStream. in-file)
          ddir      (add-trailing-slash dest-dir)
          result    (load-from-stream user ddir file-name in-stream false fileFactory jargon fileSystem)]
      (clean-return 
        fileSystem 
        (merge
          {:action "file-upload"
           :label (basename dest-path)
           :type ""
           :description ""}
          result)))))

(defn load-from-url
  "Loads the contents of the file pointed to by url-string into the file-path created
   by joining dest-dir with file-name. The resulting file in iRODS will be owned by user."
  [user dest-dir file-name url-string]
  (let [jargon-records (create-records)
        context        (:context jargon-records)
        jargon         (:jargon jargon-records)
        fileSystem     (:fileSystem context)
        fileFactory    (:fileFactory context)]
    (log/debug (str "load-from-url " user " " dest-dir " " file-name " " url-string))
    (let [in-stream (ssl/input-stream url-string)
          ddir (add-trailing-slash dest-dir)]
      (clean-return
        fileSystem
        (merge
          {:action "url-upload"
           :label file-name
           :type ""
           :description ""}
          (load-from-stream user ddir file-name in-stream true fileFactory jargon fileSystem))))))

(defn preview
  "Grabs a preview of a file in iRODS.

   Parameters:
     user - The username of the user requesting the preview.
     path - The path to the file in iRODS that will be previewed.
     size - The size (in bytes) of the preview to be created."
  [user path size]
  (let [jargon-records (create-records)
        jargon         (:jargon jargon-records)
        context        (:context jargon-records)
        fileSystem     (:fileSystem context)
        fileFactory    (:fileFactory context)]
    (log/debug (str "preview " user " " path " " size))
    (if (not (is-file? jargon path))
      {:action "preview" :status "failure" :reason "isn't a file" :path path :user user}
      (if (exists? jargon path)
        (if (is-readable? jargon user path)
          (clean-return
           fileSystem
           (.
            (let [realsize (file-size jargon path)
                  buffsize (if (<= realsize size) realsize size)
                  buff (char-array buffsize)]
              (do
                (. (IRODSFileReader. (file jargon path) fileFactory) read buff)
                (. (StringBuilder.) append buff)))
            toString))
          (clean-return 
            fileSystem 
            {:action "preview" 
             :status "failed" 
             :reason "Is not readable." 
             :path path
             :error_code ERR_NOT_READABLE}))
        (clean-return 
          fileSystem 
          {:action "preview" 
           :status "failed" 
           :reason "Does not exist."
           :error_code ERR_DOES_NOT_EXIST
           :path path})))))

(defn user-home-dir
  [staging-dir user set-owner?]
  "Returns the path to the user's home directory in our zone of iRODS.

    Parameters:
      user - String containing a username

    Returns:
      A string containing the absolute path of the user's home directory."
  (let [jargon-records (create-records)
        context        (:context jargon-records)
        fileSystem     (:fileSystem context)
        jargon         (:jargon jargon-records)]
    (log/debug (str "user-home-dir " user))
    (let [user-home (path-join staging-dir user)]
      (do
        (if (not (exists? jargon user-home))
          (. (file jargon user-home) mkdirs))
        (clean-return fileSystem user-home)))))

(defn fail-resp
  [action status reason error-code]
  {:action action :status status :reason reason :error_code error-code})

(defn metadata-get
  [user path]
  (let [jargon-records (create-records)
        jargon         (:jargon jargon-records)
        context        (:context jargon-records)
        fileSystem     (:fileSystem context)
        meta           (:metadata jargon-records)]
    (cond
     (not (exists? jargon path))
     (clean-return 
       fileSystem 
       (fail-resp "get-metadata" "failure" "Path doesn't exist." ERR_DOES_NOT_EXIST))
     
     (not (is-readable? jargon user path))
     (clean-return 
       fileSystem 
       (fail-resp "get-metadata" "failure" "Path isn't readable by user" ERR_NOT_READABLE))
     
     :else
     (clean-return 
       fileSystem 
       {:action "get-metadata"
        :status "success"
        :metadata (get-metadata meta path)}))))

(defn get-tree
  [user path]
  (let [jargon-records (create-records)
        jargon         (:jargon jargon-records)
        context        (:context jargon-records)
        fileSystem     (:fileSystem context)
        meta           (:metadata jargon-records)]
    (cond
     (not (exists? jargon path))
     (clean-return 
       fileSystem 
       (fail-resp "get-tree-urls" "failure" "Path doesn't exist." ERR_DOES_NOT_EXIST))
     
     (not (is-readable? jargon user path))
     (clean-return 
       fileSystem 
       (fail-resp "get-tree-urls" "failure" "Path isn't readable by user" ERR_NOT_READABLE))
     
     :else
     (let [value (:value (first (get-attribute meta path "tree-urls")))]
       (log/warn value)
       (clean-return fileSystem (json/read-json value))))))

(defn metadata-set
  [user path avu-map]
  (let [jargon-records (create-records)
        jargon         (:jargon jargon-records)
        context        (:context jargon-records)
        fileSystem     (:fileSystem context)
        meta           (:metadata jargon-records)]
    (cond
     (= "failure" (:status avu-map))
     (clean-return 
       fileSystem 
       (fail-resp "set-metadata" "failure" "Bad JSON." ERR_INVALID_JSON))
     
     (not (exists? jargon path))
     (clean-return 
       fileSystem 
       (fail-resp "set-metadata" "failure" "Path doesn't exist." ERR_DOES_NOT_EXIST))
     
     (not (is-writeable? jargon user path))
     (clean-return 
       fileSystem 
       (fail-resp "set-metadata" "failure" "Path isn't writeable by user." ERR_NOT_WRITEABLE))
     
     :else
     (do
       (set-metadata meta path (:attr avu-map) (:value avu-map) (:unit avu-map))
       (clean-return fileSystem {:action "set-metadata" :status "success" :path path :user user})))))

(defn set-tree
  [user path tree-urls]
  (let [jargon-records (create-records)
        jargon         (:jargon jargon-records)
        context        (:context jargon-records)
        fileSystem     (:fileSystem context)
        meta           (:metadata jargon-records)]
    (cond
     (not (exists? jargon path))
     (fail-resp "set-tree-urls" "failure" "Path doesn't exist." ERR_DOES_NOT_EXIST)
     
     (not (is-writeable? jargon user path))
     (fail-resp "set-tree-urls" "failure" "Path isn't writeable by user." ERR_NOT_WRITEABLE)
     
     :else
     (let [tree-urls (:tree-urls tree-urls)
           curr-val (if (attribute? meta path "tree-urls")
                      (json/read-json (:value (first (get-attribute meta path "tree-urls"))))
                      [])
           new-val (json/json-str (flatten (conj curr-val tree-urls)))]
       (set-metadata meta path "tree-urls" new-val "")
       {:action "set-tree-urls" :status "success" :path path :user user}))))

(defn metadata-delete
  [user path attr]
  (let [jargon-records (create-records)
        jargon         (:jargon jargon-records)
        context        (:context jargon-records)
        fileSystem     (:fileSystem context)
        meta           (:metadata jargon-records)] 
    (cond
     (not (exists? jargon path))
     (clean-return 
       fileSystem 
       (fail-resp "delete-metadata" "failure" "Path doesn't exist." ERR_DOES_NOT_EXIST))
     
     (not (is-writeable? jargon user path))
     (clean-return 
       fileSystem 
       (fail-resp "delete-metadata" "failure" "Path isn't writeable by user." ERR_NOT_WRITEABLE))
     
     :else
     (do
       (delete-metadata meta path attr)
       (clean-return 
         fileSystem 
         {:action "delete-metadata" :status "success" :path path :user user})))))

(defn path-exists?
  [path]
  (let [jargon-records (create-records)
        context        (:context jargon-records)
        fileSystem     (:fileSystem context)
        jargon         (:jargon jargon-records)]
    (clean-return fileSystem (exists? jargon path))))

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
  (let [jargon-records (create-records)
        jargon         (:jargon jargon-records)
        context        (:context jargon-records)
        fileSystem     (:fileSystem context)
        meta           (:metadata jargon-records)]
    (cond
     (not (exists? jargon path))
     (clean-return 
       fileSystem 
       (fail-resp "manifest" "failure" "path doesn't exist." ERR_DOES_NOT_EXIST))
     
     (not (is-file? jargon path))
     (clean-return 
       fileSystem 
       (fail-resp "manifest" "failure" "path isn't a file." ERR_NOT_A_FILE))
     
     (not (is-readable? jargon user path))
     (clean-return 
       fileSystem 
       (fail-resp "manifest" "failure" "path isn't readable." ERR_NOT_READABLE))
     
     :else
     (let [manifest         {:action "manifest"
                             :tree-urls (format-tree-urls (get-attribute meta path "tree-urls"))}
           file-size        (file-size jargon path)
           preview-path     (str "file/preview?user=" (cdc/url-encode user) "&path=" (cdc/url-encode path))
           rawcontents-path (str "file/download?user=" (cdc/url-encode user) "&path=" (cdc/url-encode path))
           rc-no-disp       (str rawcontents-path "&attachment=0")]
       (cond
        (extension? path ".png")
        (clean-return fileSystem (merge manifest {:png rawcontents-path}))
        
        (extension? path ".pdf")
        (clean-return fileSystem (merge manifest {:pdf rc-no-disp}))
        
        (>= file-size data-threshold)
        (clean-return fileSystem (merge manifest {:preview preview-path}))
        
        :else
        (clean-return fileSystem (merge manifest {:rawcontents rawcontents-path})))))))
