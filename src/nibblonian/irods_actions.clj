(ns nibblonian.irods-actions
  (:require [clojure.tools.logging :as log]
            [clojure.java.io :as ds]
            [nibblonian.ssl :as ssl]
            [clojure.data.codec.base64 :as b64]
            [ring.util.codec :as cdc]
            [clojure.data.json :as json]
            [clojure-commons.file-utils :as ft]
            [clojure.string :as string])
  (:use [clj-jargon.jargon]
        [nibblonian.error-codes]
        [slingshot.slingshot :only [try+ throw+]]))

(def IPCRESERVED "ipc-reserved-unit")

(defn filter-unreadable
  [metadata-list]
   (into [] (filter #(:read (:permissions %)) metadata-list)))

(defn filter-labels
  [files filter-files]
  (let [ff (set filter-files)]
    (into [] (filter #(not (contains? ff (:label %))) files))))

(defn list-files
  [user list-entries dirpath filter-files]
  (into 
    [] 
    (filter
      #(and (get-in %1 [:permissions :read]) 
            (not (contains? filter-files (:id %1)))
            (not (contains? filter-files (:label %1)))) 
      (map
        #(let [abspath (.getFormattedAbsolutePath %1)
               label   (ft/basename abspath)
               perms   (dataobject-perm-map user abspath)
               created (str (long (.. %1 getCreatedAt getTime)))
               lastmod (str (long (.. %1 getModifiedAt getTime)))
               size    (str (.getDataSize %1))]
           (assoc 
             {}
             :id            abspath
             :label         label
             :permissions   perms
             :date-created  created
             :date-modified lastmod
             :file-size     size)) 
        list-entries))))

(defn has-sub-dirs
  [user abspath]
  true)

(defn dir-map-entry
  [user list-entry]
  (let [abspath (.getFormattedAbsolutePath list-entry)
        label   (ft/basename abspath)
        perms   (collection-perm-map user abspath)
        created (str (long (.. list-entry getCreatedAt getTime)))
        lastmod (str (long (.. list-entry getModifiedAt getTime)))
        size    (str (.getDataSize list-entry))]
    (hash-map
      :id            abspath
      :label         label
      :permissions   perms
      :date-created  created
      :date-modified lastmod
      :hasSubDirs    true
      :file-size     size)))

(defn list-dirs
  [user list-entries dirpath filter-files]
  (into 
    [] 
    (filter
      #(and (get-in %1 [:permissions :read])
            (not (contains? filter-files (:id %1)))
            (not (contains? filter-files (:label %1)))) 
      (map #(dir-map-entry user %) list-entries))))

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
  ([user path filter-files]
     (list-dir user path true filter-files))

  ([user path include-files filter-files]
     (log/warn (str "list-dir " user " " path))
     (with-jargon
       (when-not (user-exists? user)
         (throw+ {:error_code ERR_NOT_A_USER
                  :user user}))
       
       (when-not (exists? path)
         (throw+ {:error_code ERR_DOES_NOT_EXIST
                  :path path}))
       
       (when-not (is-readable? user path)
         (throw+ {:error_code ERR_NOT_READABLE
                  :path path
                  :user user}))
       
       (let [fixed-path   (ft/rm-last-slash path)
             ff           (set filter-files)
             all-entries  (.listDataObjectsAndCollectionsUnderPath (:lister cm) fixed-path)
             file-entries (filter 
                            #(.isDataObject %1) 
                            all-entries)
             dir-entries  (filter 
                            #(.isCollection %1) 
                            all-entries)
             files        (list-files user file-entries (ft/rm-last-slash path) ff)
             dirs         (list-dirs user dir-entries (ft/rm-last-slash path) ff)
             add-files    #(if include-files
                             (assoc %1 :files files)
                             %1)]
         (-> {}
             (assoc
               :id path
               :label         (ft/basename path)
               :hasSubDirs    (pos? (count dirs))
               :date-created  (created-date path)
               :date-modified (lastmod-date path)
               :permissions   (collection-perm-map user path)
               :folders       dirs)
             add-files)))))

(defn create
  "Creates a directory at 'path' in iRODS and sets the user to 'user'.

   Parameters:
     user - String containing the username of the user requesting the directory.
     path - The path that the directory will be created at in iRODS.

   Returns a map of the format {:action \"create\" :path \"path\"}"
  [user path]
  (log/debug (str "create " user " " path))
  (with-jargon
    (when (not (user-exists? user))
      (throw+ {:error_code ERR_NOT_A_USER
               :user user}))
    
    (when (not (collection-writeable? user (ft/dirname path)))
      (throw+ {:path path
               :error_code ERR_NOT_WRITEABLE}))
    
    (when (exists? path)
      (throw+ {:path path
               :error_code ERR_EXISTS}))
    
    (mkdir path)
    (set-owner path user)
    {:path path 
     :permissions (collection-perm-map user path)}))

(defn- del
  "Performs some validation and calls delete.

   Parameters:
     user - username of the user requesting the directory deletion.
     paths - a sequence of strings containing directory paths.

   Returns a map describing the success or failure of the deletion command."
  [user paths type-func? type-error]
  (with-jargon
    (when (not (user-exists? user))
      (throw+ {:error_code ERR_NOT_A_USER
               :user user}))
    
    ;Make sure all of the paths exist.
    (when (not (paths-exist? paths))
      (throw+ {:paths (into [] (for [path paths :when (not (exists? path))] path))
               :error_code ERR_DOES_NOT_EXIST})) 
    
    ;Make sure all of the paths are writeable.
    (when (not (paths-writeable? user paths))
      (throw+ {:paths (into [] (for [path paths :when (not (is-writeable? user path))] path))
               :error_code ERR_NOT_WRITEABLE}))
    
    ;Make sure all of the paths are directories.
    (when (not (every? true? (map #(type-func? %) paths)))
      (throw+ {:error_code type-error
               :paths (into [] (for [p paths :when (not (type-func? p))] p))}))
    
    (doseq [p paths] 
      (delete p))
    
    {:paths paths}))

(defn delete-dirs
  [user paths]
  (del user paths is-dir? ERR_NOT_A_FOLDER))

(defn delete-files
  [user paths]
  (del user paths is-file? ERR_NOT_A_FILE))

(defn- mv
  "Moves directories listed in 'sources' into the directory listed in 'dest'. This
   works by calling move and passing it move-dir."
  [user sources dest type-func? type-error]
  (with-jargon
    (let [path-list  (conj sources dest)
          dest-paths (into [] (map #(ft/path-join dest (ft/basename %)) sources))
          types?     (every? true? (map #(type-func? %) sources))]
      (when (not (user-exists? user))
        (throw+ {:error_code ERR_NOT_A_USER
                 :user user}))
      
      ;Make sure that all source paths in the request actually exist.
      (when (not (paths-exist? sources))
        (throw+ {:error_code ERR_DOES_NOT_EXIST 
                 :paths (into [] (for [path sources :when (not (exists? path))] path))}))
      
      ;Make sure that the destination directory actually exists.
      (when (not (exists? dest))
        (throw+ {:error_code ERR_DOES_NOT_EXIST  
                 :paths [dest]}))
      
      ;dest must be a directory.
      (when (not (is-dir? dest))
        (throw+ {:error_code ERR_NOT_A_FOLDER
                 :path dest}))
      
      ;Make sure all the paths in the request are writeable.
      (when (not (paths-writeable? user sources))
        (throw+ {:error_code ERR_NOT_WRITEABLE
                 :paths (into [] (for [path sources :when (not (is-writeable? user path))] path))}))
      
      ;Make sure the destination directory is writeable.
      (when (not (is-writeable? user dest))
        (throw+ {:error_code ERR_NOT_WRITEABLE
                 :path (ft/dirname dest)}))
      
      ;Make sure that the destination paths don't exist already.
      (when (not (every? false? (map exists? dest-paths)))
        (throw+ {:error_code ERR_EXISTS
                 :paths (into [] (filter (fn [p] (exists? p)) dest-paths))}))
      
      ;Make sure that everything in sources is the correct type.
      (when (not types?)
        (throw+ {:error_code type-error
                 :paths path-list}))
      
      (move-all sources dest)
      {:sources sources :dest dest})))

(defn move-directories
  [user sources dest]
  (mv user sources dest is-dir? ERR_NOT_A_FOLDER))

(defn move-files
  [user sources dest]
  (mv user sources dest is-file? ERR_NOT_A_FILE))

(defn- rname
  "High-level file renaming. Calls rename-func, passing it file-rename as the mv-func param."
  [user source dest type-func? type-error]
  (with-jargon
    (when (not (user-exists? user))
      (throw+ {:error_code ERR_NOT_A_USER
               :user user}))
    
    (when (not (exists? source))
      (throw+ {:path source
               :error_code ERR_DOES_NOT_EXIST}))
    
    (when (not (is-writeable? user source))
      (throw+ {:error_code ERR_NOT_WRITEABLE
               :path source}))
    
    (when (not (type-func? source))
      (throw+ {:paths source
               :error_code type-error}))
    
    (when (exists? dest)
      (throw+ {:error_code ERR_EXISTS
               :path dest}))
    
    (let [result (move source dest)]
      (when (not (nil? result))
        (throw+ {:error_code ERR_INCOMPLETE_RENAME
                 :paths result
                 :user user}))
      {:source source :dest dest :user user})))

(defn rename-file
  [user source dest]
  (rname user source dest is-file? ERR_NOT_A_FILE))

(defn rename-directory
  [user source dest]
  (rname user source dest is-dir? ERR_NOT_A_FOLDER))

(defn- preview-buffer
  [path size]
  (let [realsize (file-size path)
        buffsize (if (<= realsize size) realsize size)
        buff (char-array buffsize)]
    (read-file path buff)
    (. (StringBuilder.) append buff)))

(defn preview
  "Grabs a preview of a file in iRODS.

   Parameters:
     user - The username of the user requesting the preview.
     path - The path to the file in iRODS that will be previewed.
     size - The size (in bytes) of the preview to be created."
  [user path size]
  (with-jargon
    (log/debug (str "preview " user " " path " " size))
    (when (not (user-exists? user))
      (throw+ {:error_code ERR_NOT_A_USER
               :user user}))
    
    (when (not (exists? path))
      (throw+ {:error_code ERR_DOES_NOT_EXIST
               :path path}))
    
    (when (not (is-readable? user path))
      (throw+ {:path path
               :error_code ERR_NOT_READABLE}))
    
    (when (not (is-file? path))
      (throw+ {:error_code ERR_NOT_A_FILE
               :path path 
               :user user}))
    
    (if (= (file-size path) 0)
      ""
      (.toString (preview-buffer path size)))))

(defn user-home-dir
  [staging-dir user set-owner?]
  "Returns the path to the user's home directory in our zone of iRODS.

    Parameters:
      user - String containing a username

    Returns:
      A string containing the absolute path of the user's home directory."
  (with-jargon
    (when (not (user-exists? user))
      (throw+ {:error_code ERR_NOT_A_USER
               :user user}))
    
    (let [user-home (ft/path-join staging-dir user)]
      (if (not (exists? user-home))
        (mkdirs user-home))
      user-home)))

(defn metadata-get
  [user path]
  (with-jargon
    (when (not (user-exists? user))
      (throw+ {:error_code ERR_NOT_A_USER
               :user user}))
    
    (when (not (exists? path))
      (throw+ {:error_code ERR_DOES_NOT_EXIST}))
    
    (when (not (is-readable? user path))
      (throw+ {:error_code ERR_NOT_READABLE}))
    
    (let [fix-unit #(if (= (:unit %1) IPCRESERVED) (assoc %1 :unit "") %1)
          avu (map fix-unit (get-metadata (ft/rm-last-slash path)))]
      {:metadata avu})))

(defn get-tree
  [user path]
  (with-jargon
    (when (not (user-exists? user))
      (throw+ {:error_code ERR_NOT_A_USER
               :user user}))
    
    (when (not (exists? path))
      (throw+ {:error_code ERR_DOES_NOT_EXIST}))
    
    (when (not (is-readable? user path))
      (throw+ {:error_code ERR_NOT_READABLE}))
    
    (let [value (:value (first (get-attribute path "tree-urls")))]
      (log/warn value)
      (json/read-json value))))

(defn metadata-set
  [user path avu-map]
  (with-jargon
    (when (not (user-exists? user))
      (throw+ {:error_code ERR_NOT_A_USER
               :user user}))
    
    (when (= "failure" (:status avu-map))
      (throw+ {:error_code ERR_INVALID_JSON}))
    
    (when (not (exists? path))
      (throw+ {:error_code ERR_DOES_NOT_EXIST}))
    
    (when (not (is-writeable? user path))
      (throw+ {:error_code ERR_NOT_WRITEABLE}))
    
    (let [new-path (ft/rm-last-slash path)
          new-attr (:attr avu-map)
          new-val  (:value avu-map)
          new-unit (if (string/blank? (:unit avu-map)) IPCRESERVED (:unit avu-map))]
      (set-metadata new-path new-attr new-val new-unit)
      {:path new-path :user user})))

(defn encode-str
  [str-to-encode]
  (String. (b64/encode (.getBytes str-to-encode))))

(defn workaround-delete
  "Gnarly workaround for a bug (I think) in Jargon. If a value
   in an AVU is formatted a certain way, it can't be deleted.
   We're base64 encoding the value before deletion to ensure
   that the deletion will work."
  [path attr]
  (let [{:keys [attr value unit]} (first (get-attribute path attr))]
    (set-metadata path attr (encode-str value) unit)))

(defn metadata-batch-set
  [user path adds-dels]
  (with-jargon
    (when (not (user-exists? user))
      (throw+ {:error_code ERR_NOT_A_USER
               :user user}))
    
    (when (not (exists? path))
      (throw+ {:error_code ERR_DOES_NOT_EXIST}))
    
    (when (not (is-writeable? user path))
      (throw+ {:error_code ERR_NOT_WRITEABLE}))
    
    (let [adds     (:add adds-dels)
          dels     (:delete adds-dels)
          new-path (ft/rm-last-slash path)]
      
      (doseq [del dels]
        (when (attribute? new-path del)
          (workaround-delete new-path del)
          (delete-metadata new-path del)))
      
      (doseq [avu adds]
        (let [new-attr (:attr avu)
              new-val  (:value avu)
              new-unit (if (string/blank? (:unit avu)) IPCRESERVED (:unit avu))]
          (set-metadata new-path new-attr new-val new-unit)))
      {:path (ft/rm-last-slash path) :user user})))

(defn set-tree
  [user path tree-urls]
  (with-jargon
    (when (not (user-exists? user))
      (throw+ {:error_code ERR_NOT_A_USER
               :user user}))
    
    (when (not (exists? path))
      (throw+ {:error_code ERR_DOES_NOT_EXIST}))
    
    (when (not (is-writeable? user path))
      (throw+ {:error_code ERR_NOT_WRITEABLE}))
    
    (let [tree-urls (:tree-urls tree-urls)
          curr-val  (if (attribute? path "tree-urls")
                      (json/read-json (:value (first (get-attribute path "tree-urls"))))
                      [])
          new-val (json/json-str (flatten (conj curr-val tree-urls)))]
      (set-metadata path "tree-urls" new-val "")
      {:path path :user user})))

(defn metadata-delete
  [user path attr]
  (with-jargon
    (when (not (user-exists? user))
      (throw+ {:error_code ERR_NOT_A_USER
               :user user}))
    
    (when (not (exists? path))
      (throw+ {:error_code ERR_DOES_NOT_EXIST}))
    
    (when (not (is-writeable? user path))
      (throw+ {:error_code ERR_NOT_WRITEABLE}))
    
    (workaround-delete path attr)
    (delete-metadata path attr)
    {:path path :user user}))

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
    (when (not (user-exists? user))
      (throw+ {:error_code ERR_NOT_A_USER
               :user user}))
    
    (when (not (exists? path))
      (throw+ {:error_code ERR_DOES_NOT_EXIST}))
    
    (when (not (is-file? path))
      (throw+ {:error_code ERR_NOT_A_FILE}))
    
    (when (not (is-readable? user path))
      (throw+ {:error_code ERR_NOT_READABLE}))
    
    (let [manifest         {:action "manifest"
                            :tree-urls (format-tree-urls (get-attribute path "tree-urls"))}
          file-size        (file-size path)
          preview-path     (str "file/preview?user=" (cdc/url-encode user) "&path=" (cdc/url-encode path))
          rawcontents-path (str "display-download?user=" (cdc/url-encode user) "&path=" (cdc/url-encode path))
          rc-no-disp       (str rawcontents-path "&attachment=0")]
      (cond
        (extension? path ".png")
        (merge manifest {:png rawcontents-path})
        
        (extension? path ".pdf")
        (merge manifest {:pdf rc-no-disp})
        
        :else
        (merge manifest {:preview preview-path})))))

(defn download-file
  [user file-path]
  (with-jargon
    (when (not (user-exists? user))
      (throw+ {:error_code ERR_NOT_A_USER
               :user user}))
    
    (when (not (exists? file-path))
      (throw+ {:error_code ERR_DOES_NOT_EXIST
               :path file-path}))
    
    (when (not (is-readable? user file-path))
      (throw+ {:error_code ERR_NOT_WRITEABLE 
               :path file-path}))
    
    (if (= (file-size file-path) 0)
      ""
      (input-stream file-path))))

(defn download
  [user filepaths]
  (with-jargon
    (when (not (user-exists? user))
      (throw+ {:error_code ERR_NOT_A_USER}))
    
    (let [cart-key   (str (System/currentTimeMillis))
          account    (:irodsAccount cm)
          irods-host (.getHost account)
          irods-port (.getPort account)
          irods-zone (.getZone account)
          irods-dsr  (.getDefaultStorageResource account)
          user-home  (ft/path-join "/" @zone "home" user)
          passwd     (store-cart user cart-key filepaths)]
      {:action "download"
       :status "success"
       :data
       {:user user
        :home user-home
        :password passwd
        :host irods-host
        :port irods-port
        :zone irods-zone
        :defaultStorageResource irods-dsr
        :key cart-key}})))

(defn upload
  [user]
  (with-jargon
    (when (not (user-exists? user))
      (throw+ {:error_code ERR_NOT_A_USER}))
    
    (let [cart-key   (str (System/currentTimeMillis))
          account    (:irodsAccount cm)
          irods-host (.getHost account)
          irods-port (.getPort account)
          irods-zone (.getZone account)
          user-home  (ft/path-join "/" @zone "home" user)
          irods-dsr  (.getDefaultStorageResource account)
          passwd     (temp-password user)]
      {:action "upload"
       :status "success"
       :data
       {:user user
        :home user-home
        :password passwd
        :host irods-host
        :port irods-port
        :zone irods-zone
        :defaultStorageResource irods-dsr
        :key cart-key}})))
