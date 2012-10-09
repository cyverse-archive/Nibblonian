(ns nibblonian.irods-actions
  (:require [clojure.tools.logging :as log]
            [clojure.java.io :as ds]
            [clojure.data.codec.base64 :as b64]
            [ring.util.codec :as cdc]
            [clojure.data.json :as json]
            [clojure-commons.file-utils :as ft]
            [clojure.string :as string]
            [nibblonian.validators :as validators])
  (:use [clj-jargon.jargon :exclude [init]]
        clojure-commons.error-codes
        [nibblonian.config :exclude [init]]
        [slingshot.slingshot :only [try+ throw+]])
  (:import [org.apache.tika Tika]))

(def IPCRESERVED "ipc-reserved-unit")

(defn not-filtered?
  [cm user fpath ff]
  (and (not (contains? ff fpath)) 
       (not (contains? ff (ft/basename fpath)))
       (is-readable? cm user fpath)))

(defn directory-listing
  [cm user dirpath filter-files]
  (let [fs (:fileSystemAO cm)
        ff (set filter-files)]
    (filterv 
      #(not-filtered? cm user %1 ff)
      (map
        #(ft/path-join dirpath %) 
        (.getListInDir fs (file dirpath))))))

(defn has-sub-dir [user abspath] true)

(defn filtered-user-perms
  [cm user abspath]
  (filter
   #(not (or (= (:user %1) user)
             (= (:user %1) (irods-user))))
   (list-user-perms cm abspath)))

(defn- list-perm
  [cm user abspath]
  {:path abspath
   :user-permissions (filtered-user-perms cm user abspath)})

(defn list-perms
  [user abspaths]
  (with-jargon (jargon-config) [cm]
    (validators/user-exists user)
    (validators/all-paths-exist abspaths)
    (validators/user-owns-paths user abspaths)
    (mapv (partial list-perm user) abspaths)))

(defn date-mod-from-stat 
  [stat] 
  (str (long (.. stat getModifiedAt getTime))))

(defn date-created-from-stat
  [stat]
  (str (long (.. stat getCreatedAt getTime))))

(defn size-from-stat
  [stat]
  (str (.getObjSize stat)))

(defn dir-map-entry
  ([cm user list-entry]
     (dir-map-entry
      cm
      user
      list-entry
      (ft/basename (.getAbsolutePath list-entry))))
  ([cm user list-entry label]
     (let [abspath (.getAbsolutePath list-entry)
           stat    (.initializeObjStatForFile list-entry)] 
       (hash-map      
        :id            abspath
        :label         label
        :permissions   (collection-perm-map cm user abspath)
        :hasSubDirs    true
        :date-created  (date-created-from-stat stat)
        :date-modified (date-mod-from-stat stat)
        :file-size     (size-from-stat stat)))))

(defn file-map-entry
  [cm user list-entry]
  (let [abspath (.getAbsolutePath list-entry)
        stat    (.initializeObjStatForFile list-entry)]
    (hash-map
      :id            abspath
      :label         (ft/basename abspath)
      :permissions   (dataobject-perm-map cm user abspath)
      :date-created  (date-created-from-stat stat)
      :date-modified (date-mod-from-stat stat)
      :file-size     (size-from-stat stat))))

(defn list-dirs
  [cm user list-entries dirpath filter-files]
  (filterv
    #(and (get-in %1 [:permissions :read])
          (not (contains? filter-files (:id %1)))
          (not (contains? filter-files (:label %1))))
    (map #(dir-map-entry cm user %1) list-entries)))

(defn list-files
  [cm user list-entries dirpath filter-files]
  (filterv
    #(and (get-in %1 [:permissions :read]) 
          (not (contains? filter-files (:id %1)))
          (not (contains? filter-files (:label %1)))) 
    (map #(file-map-entry cm user %1) list-entries)))

(defn list-in-dir
  [cm fixed-path]
  (let [ffilter (proxy [java.io.FileFilter] [] (accept [stuff] true))] 
    (.getListInDirWithFileFilter 
      (:fileSystemAO cm) 
      (file cm fixed-path) 
      ffilter)))

(defn partition-files-folders
  [cm fixed-path]
  (group-by 
    #(.isDirectory %1)
    (list-in-dir cm fixed-path)))

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
     (list-dir user path true filter-files false))

  ([user path include-files filter-files set-own?]
     (log/warn (str "list-dir " user " " path))
     
     (with-jargon (jargon-config) [cm]
       (validators/user-exists cm user)
       (validators/path-exists cm path)

       (when (and set-own? (not (owns? cm user path)))
         (set-permissions cm user path false false true))
       
       (validators/path-readable cm user path)
       
       (let [fixed-path   (ft/rm-last-slash path)
             ff           (set filter-files)
             parted-files (partition-files-folders cm fixed-path)
             dir-entries  (or (get parted-files true) (vector))
             file-entries (or (get parted-files false) (vector))
             dirs         (list-dirs cm user dir-entries fixed-path ff)
             add-files    #(if include-files
                             (assoc %1 :files (list-files cm user file-entries fixed-path ff))
                             %1)]
         (-> {}
           (assoc
             :id path
             :label         (ft/basename path)
             :hasSubDirs    true
             :date-created  (created-date cm path)
             :date-modified (lastmod-date cm path)
             :permissions   (collection-perm-map cm user path)
             :folders       dirs)
           add-files)))))

(defn root-listing
  ([user root-path]
     (root-listing user root-path (ft/basename root-path)))
  
  ([user root-path label]
     (root-listing user root-path label false))
  
  ([user root-path label set-own?]
    (with-jargon (jargon-config) [cm]
      (validators/user-exists cm user)
      (validators/path-exists cm root-path)

      (when (and set-own? (not (owns? cm user root-path)))
        (set-permissions cm user root-path false false true))

      (validators/path-readable cm user root-path)
      (dir-map-entry cm user (file cm root-path) label))))

(defn user-trash-dir
  [user])

(defn create
  "Creates a directory at 'path' in iRODS and sets the user to 'user'.

   Parameters:
     user - String containing the username of the user requesting the directory.
     path - The path that the directory will be created at in iRODS.

   Returns a map of the format {:action \"create\" :path \"path\"}"
  [user path]
  (log/debug (str "create " user " " path))
  (with-jargon (jargon-config) [cm]
    (validators/user-exists cm user)
    (validators/path-writeable cm user (ft/dirname path))
    (validators/path-not-exists cm path)
    
    (mkdir cm path)
    (set-owner cm path user)
    (fix-owners cm path user (irods-user))
    {:path path 
     :permissions (collection-perm-map cm user path)}))

(defn- del
  "Performs some validation and calls delete.

   Parameters:
     user - username of the user requesting the directory deletion.
     paths - a sequence of strings containing directory paths.

   Returns a map describing the success or failure of the deletion command."
  [user paths type-func? type-error]
  (let [home-matcher #(= (str "/" (irods-zone) "/home/" user)
                         (ft/rm-last-slash %1))] 
    (with-jargon (jargon-config) [cm]
      (validators/user-exists cm user)
      (validators/all-paths-exist cm paths)
      (validators/all-paths-writeable cm user paths)
      (validators/paths-satisfy-predicate cm paths type-func? type-error)
      
      (when (some true? (mapv home-matcher paths))
        (throw+ {:error_code ERR_NOT_AUTHORIZED 
                 :paths (filterv home-matcher paths)}))
      
      (doseq [p paths] 
        (delete cm p))
      
      {:paths paths})))

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
  (with-jargon (jargon-config) [cm]
    (let [path-list  (conj sources dest)
          dest-paths (mapv #(ft/path-join dest (ft/basename %)) sources)
          types?     (every? true? (map #(type-func? cm %) sources))]
      (validators/user-exists cm user)
      (validators/all-paths-exist cm sources)
      (validators/all-paths-exist cm [dest])
      (validators/path-is-dir cm dest)
      (validators/all-paths-writeable cm user sources)
      (validators/path-writeable cm user dest)
      (validators/no-paths-exist cm dest-paths)
      (validators/paths-satisfy-predicate cm sources type-func? type-error)
      (move-all cm sources dest)
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
  (with-jargon (jargon-config) [cm]
    (validators/user-exists cm user)
    (validators/path-exists cm source)
    (validators/path-writeable cm user source)
    (validators/path-satisfies-predicate cm source type-func? type-error)
    (validators/path-not-exists cm dest)
    
    (let [result (move source dest)]
      (when-not (nil? result)
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
  [cm path size]
  (let [realsize (file-size cm path)
        buffsize (if (<= realsize size) realsize size)
        buff     (char-array buffsize)]
    (read-file cm path buff)
    (.append (StringBuilder.) buff)))

(defn preview
  "Grabs a preview of a file in iRODS.

   Parameters:
     user - The username of the user requesting the preview.
     path - The path to the file in iRODS that will be previewed.
     size - The size (in bytes) of the preview to be created."
  [user path size]
  (with-jargon (jargon-config) [cm]
    (log/debug (str "preview " user " " path " " size))
    (validators/user-exists cm user)
    (validators/path-exists cm path)
    (validators/path-readable cm user path)
    (validators/path-is-file cm path)
    
    (if (zero? (file-size cm path))
      ""
      (str (preview-buffer cm path size)))))

(defn user-home-dir
  ([user]
     (ft/path-join "/" (irods-zone) "home" user))
  ([staging-dir user set-owner?]
     (with-jargon (jargon-config) [cm]
       (validators/user-exists cm user)
       
       (let [user-home (ft/path-join staging-dir user)]
         (if (not (exists? cm user-home))
           (mkdirs cm user-home))
         user-home))))

(defn metadata-get
  [user path]
  (with-jargon (jargon-config) [cm]
    (validators/user-exists cm user)
    (validators/path-exists cm path)
    (validators/path-readable cm user path)
    
    (let [fix-unit #(if (= (:unit %1) IPCRESERVED) (assoc %1 :unit "") %1)
          avu      (map fix-unit (get-metadata cm (ft/rm-last-slash path)))]
      {:metadata avu})))

(defn get-tree
  [user path]
  (with-jargon (jargon-config) [cm]
    (validators/user-exists cm user)
    (validators/path-exists cm path)
    (validators/path-readable cm user path)
    
    (let [value (:value (first (get-attribute cm path "tree-urls")))]
      (log/warn value)
      (json/read-json value))))

(defn metadata-set
  [user path avu-map]
  (with-jargon (jargon-config) [cm]
    (validators/user-exists cm user)
    
    (when (= "failure" (:status avu-map))
      (throw+ {:error_code ERR_INVALID_JSON}))
    
    (validators/path-exists cm path)
    (validators/path-writeable cm user path)
    
    (let [new-unit (if (string/blank? (:unit avu-map))
                     IPCRESERVED
                     (:unit avu-map))]
      (set-metadata
       cm
       (ft/rm-last-slash path)
       (:attr avu-map)
       (:value avu-map)
       new-unit)
      {:path (ft/rm-last-slash path) :user user})))

(defn encode-str
  [str-to-encode]
  (String. (b64/encode (.getBytes str-to-encode))))

(defn workaround-delete
  "Gnarly workaround for a bug (I think) in Jargon. If a value
   in an AVU is formatted a certain way, it can't be deleted.
   We're base64 encoding the value before deletion to ensure
   that the deletion will work."
  [cm path attr]
  (let [{:keys [attr value unit]} (first (get-attribute path attr))]
    (set-metadata cm path attr (encode-str value) unit)))

(defn metadata-batch-set
  [user path adds-dels]
  (with-jargon (jargon-config) [cm]
    (validators/user-exists cm user)
    (validators/path-exists cm path)
    (validators/path-writeable cm user path)
    
    (let [new-path (ft/rm-last-slash path)]
      (doseq [del (:delete adds-dels)]
        (when (attribute? cm new-path del)
          (workaround-delete cm new-path del)
          (delete-metadata cm new-path del)))
      
      (doseq [avu (:add adds-dels)]
        (let [new-unit (if (string/blank? (:unit avu)) 
                         IPCRESERVED 
                         (:unit avu))]
          (set-metadata cm new-path (:attr avu) (:value avu) new-unit)))
      {:path (ft/rm-last-slash path) :user user})))

(defn tree-urls-value
  [cm path]
  (-> (get-attribute cm path "tree-urls") first :value json/read-json))

(defn current-tree-urls-value
  [cm path]
  (if (attribute? cm path "tree-urls")
    (tree-urls-value cm path)
    []))

(defn add-tree-urls
  [curr-val tree-urls]
  (-> (conj curr-val tree-urls) flatten json/json-str))

(defn set-new-tree-urls
  [cm path tree-urls]
  (set-metadata
   cm
   path
   "tree-urls"
   (add-tree-urls (current-tree-urls-value path) tree-urls)
   ""))

(defn set-tree
  [user path tree-urls]
  (with-jargon (jargon-config) [cm]
    (validators/user-exists cm user)
    (validators/path-exists cm path)
    (validators/path-writeable cm user path)
    (set-new-tree-urls cm path (:tree-urls tree-urls))
    {:path path :user user}))

(defn metadata-delete
  [user path attr]
  (with-jargon (jargon-config) [cm]
    (validators/user-exists cm user)
    (validators/path-exists cm path)
    (validators/path-writeable cm user path)
    (workaround-delete cm path attr)
    (delete-metadata cm path attr)
    {:path path :user user}))

(defn path-exists?
  [path]
  (with-jargon (jargon-config) [cm] (exists? cm path)))

(defn path-stat
  [path]
  (with-jargon (jargon-config) [cm]
    (validators/path-exists cm path)
    (stat cm path)))

(defn- format-tree-urls
  [treeurl-maps]
  (if (pos? (count treeurl-maps))
    (json/read-json (:value (first (seq treeurl-maps))))
    []))

(defn preview-url
  [user path]
  (str "file/preview?user=" (cdc/url-encode user) 
       "&path=" 
       (cdc/url-encode path)))

(defn content-type
  [cm path]
  (.detect (Tika.) (input-stream cm path)))

(defn manifest
  [user path data-threshold]
  (with-jargon (jargon-config) [cm]
    (validators/user-exists cm user)
    (validators/path-exists cm path)
    (validators/path-is-file cm path)
    (validators/path-readable cm user path)
    
    {:action "manifest"
     :content-type (content-type cm path)
     :tree-urls (format-tree-urls (get-attribute cm path "tree-urls"))
     :preview (preview-url user path)}))

(defn download-file
  [user file-path]
  (with-jargon (jargon-config) [cm]
    (validators/user-exists cm user)
    (validators/path-exists cm file-path)
    (validators/path-readable cm user file-path)
    
    (if (zero? (file-size cm file-path))
      ""
      (input-stream cm file-path))))

(defn download
  [user filepaths]
  (with-jargon (jargon-config) [cm]
    (validators/user-exists cm user)
    
    (let [cart-key   (str (System/currentTimeMillis))
          account    (:irodsAccount cm)]
      {:action "download"
       :status "success"
       :data
       {:user user
        :home (ft/path-join "/" (irods-zone) "home" user)
        :password (store-cart cm user cart-key filepaths)
        :host (.getHost account)
        :port (.getPort account)
        :zone (.getZone account)
        :defaultStorageResource (.getDefaultStorageResource account)
        :key cart-key}})))

(defn upload
  [user]
  (with-jargon (jargon-config) [cm]
    (validators/user-exists cm user)
    
    (let [account (:irodsAccount cm)]
      {:action "upload"
       :status "success"
       :data
       {:user user
        :home (ft/path-join "/" (irods-zone) "home" user)
        :password (temp-password cm user)
        :host (.getHost account)
        :port (.getPort account)
        :zone (.getZone account)
        :defaultStorageResource (.getDefaultStorageResource account)
        :key (str (System/currentTimeMillis))}})))

(defn share
  [user share-withs fpaths perms]
  (with-jargon (jargon-config) [cm]
    (validators/user-exists cm user)
    (validators/all-users-exist cm share-withs)
    (validators/all-paths-exist cm fpaths)
    
    (when-not (every? (partial #(owns? cm %) user) fpaths)
      (throw+ {:error_code ERR_NOT_OWNER
               :paths (filterv
                        (partial #(owns? cm %) user)
                        fpaths)
               :user user}))

    (doseq [share-with share-withs]
      (doseq [fpath fpaths]
        (let [read-perm  (:read perms)
              write-perm (:write perms)
              own-perm   (:own perms)
              base-dir   (ft/path-join "/" (irods-zone))]
          
          ;;Parent directories need to be readable, otherwise
          ;;files and directories that are shared will be
          ;;orphaned in the UI.
          (loop [dir-path (ft/dirname fpath)]
            (when-not (= dir-path base-dir)
              (let [curr-perms (permissions cm share-with dir-path)
                    curr-read  (:read curr-perms)
                    curr-write (:write curr-perms)
                    curr-own   (:own curr-perms)]
                (set-permissions cm share-with dir-path true curr-write curr-own)
                (recur (ft/dirname dir-path)))))
          
          ;;Set the actual permissions on the file/directory.
          (set-permissions cm share-with fpath read-perm write-perm own-perm true)
          
          ;;If the shared item is a directory, then it needs the inheritance 
          ;;bit set. Otherwise, any files that are added to the directory will
          ;;not be shared.
          (when (is-dir? cm fpath)
            (.setAccessPermissionInherit
             (:collectionAO cm)
             (irods-zone)
             fpath
             true)))))
    
    {:user share-withs
     :path fpaths
     :permissions perms}))

(defn contains-accessible-obj?
  [cm user dpath]
  (some #(is-readable? cm user %1) (list-paths cm dpath)))

(defn contains-subdir?
  [cm dpath]
  (some #(is-dir? cm %) (list-paths cm dpath)))

(defn subdirs
  [cm dpath]
  (filter #(is-dir? cm %) (list-paths cm dpath)))

(defn some-subdirs-readable?
  [cm user parent-path]
  (some #(is-readable? cm user %1) (subdirs cm parent-path)))

(defn unshare
  "Allows 'user' to unshare file 'fpath' with user 'unshare-with'."
  [user unshare-withs fpaths]
  (with-jargon (jargon-config) [cm]
    (validators/user-exists cm user)
    (validators/all-users-exist cm unshare-withs)
    (validators/all-paths-exist cm fpaths)
    (validators/user-owns-paths cm user fpaths)

    (doseq [unshare-with unshare-withs]
      (doseq [fpath fpaths]
        (let [base-dir    (ft/path-join "/" (irods-zone))
              parent-path (ft/dirname fpath)]
          (remove-permissions cm unshare-with fpath)
          
          (when-not (and (contains-subdir? cm parent-path)
                         (some-subdirs-readable? cm user parent-path))
            (loop [dir-path parent-path]
              (when-not (or (= dir-path base-dir)
                            (contains-accessible-obj? cm unshare-with dir-path))
                (remove-permissions cm unshare-with dir-path)
                (recur (ft/dirname dir-path)))))))))
  {:user unshare-withs
   :path fpaths})

(defn sharing-data
  [cm user root-dir inc-files filter-files]
  (list-dir cm user (ft/rm-last-slash root-dir) inc-files filter-files))

(defn shared-root-listing
  [user root-dir inc-files filter-files]
  
  (with-jargon (jargon-config) [cm]
    (when-not (is-readable? cm user root-dir)
      (set-permissions
       cm
       user
       (ft/rm-last-slash root-dir)
       true
       false
       false))
    
    (assoc (sharing-data cm user root-dir inc-files filter-files)
      :label "Shared")))

(defn get-quota
  [user]
  (with-jargon (jargon-config) [cm]
    (validators/user-exists cm user)
    (quota cm user)))

(defn trim-leading-slash
  [str-to-trim]
  (string/replace-first str-to-trim #"^\/" ""))

(defn trash-relative-path
  [path name user-trash]
  (trim-leading-slash
   (ft/path-join
    (or
     (ft/dirname
      (string/replace-first path (ft/add-trailing-slash user-trash) ""))
     "")
    name)))

(defn restoration-path
  [user path name user-trash]
  (let [user-home (user-home-dir user)]
    (ft/path-join user-home (trash-relative-path path name user-trash))))

(defn restore-path
  [{:keys [user path name user-trash]}]
  (with-jargon (jargon-config) [cm]
    (validators/user-exists cm user)
    (validators/path-exists cm path)
    (validators/path-writeable cm user path)

    (let [fully-restored (restoration-path user path name user-trash)]
      (validators/path-not-exists cm fully-restored)
      (validators/path-writeable cm user (ft/dirname fully-restored))
      (move cm path fully-restored)
      {:from path :to fully-restored})))

(defn copy-path
  ([copy-map]
     (copy-path copy-map "ipc-de-copy-from"))
  
  ([{:keys [user from to]} copy-key]
     (with-jargon (jargon-config) [cm]
       (validators/user-exists cm user)
       (validators/all-paths-exist cm from)
       (validators/all-paths-readable cm user from)
       (validators/path-exists cm to)
       (validators/path-writeable cm user to)
       (validators/path-is-dir cm to)
       (validators/no-paths-exist
        cm
        (mapv #(ft/path-join to (ft/basename %)) from))
       
       ;;;Can't copy a file or directory into itself.
       (when (some true? (mapv #(= to %1) from))
         (throw+ {:error_code ERR_INVALID_COPY
                  :paths (filterv #(= to %1) from)}))
       
       (doseq [fr from]
         (copy cm fr to)
         (set-metadata
          cm
          (ft/rm-last-slash (ft/path-join to (ft/basename fr)))
          copy-key
          fr
          ""))
       
       {:sources from :dest to})))

