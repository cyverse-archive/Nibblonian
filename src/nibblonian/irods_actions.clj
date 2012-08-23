(ns nibblonian.irods-actions
  (:require [clojure.tools.logging :as log]
            [clojure.java.io :as ds]
            [clojure.data.codec.base64 :as b64]
            [ring.util.codec :as cdc]
            [clojure.data.json :as json]
            [clojure-commons.file-utils :as ft]
            [clojure.string :as string]
            [nibblonian.validators :as validators])
  (:use [clj-jargon.jargon]
        [clojure-commons.error-codes]
        [slingshot.slingshot :only [try+ throw+]])
  (:import [org.apache.tika Tika]))

(def IPCRESERVED "ipc-reserved-unit")

(defn directory-listing
  [user dirpath filter-files]
  (let [fs (:fileSystemAO cm)
        ff (set filter-files)]
    (filterv 
      #(and (not (contains? ff %1)) 
            (not (contains? ff (ft/basename %1)))
            (is-readable? user %1)) 
      (map
        #(ft/path-join dirpath %) 
        (.getListInDir fs (file dirpath))))))

(defn has-sub-dirs
  [user abspath]
  true)

(defn- list-perm
  [user abspath]
  {:path abspath
   :user-permissions (filter
                       #(not (or (= (:user %1) user)
                                 (= (:user %1) @username)))
                       (list-user-perms abspath))})

(defn list-perms
  [user abspaths]
  (with-jargon
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
  ([user list-entry]
    (dir-map-entry user list-entry (ft/basename (.getAbsolutePath list-entry))))
  ([user list-entry label]
    (let [abspath (.getAbsolutePath list-entry)
          stat    (.initializeObjStatForFile list-entry)] 
      (hash-map      
        :id            abspath
        :label         label
        :permissions   (collection-perm-map user abspath)
        :hasSubDirs    true
        :date-created  (date-created-from-stat stat)
        :date-modified (date-mod-from-stat stat)
        :file-size     (size-from-stat stat)))))

(defn file-map-entry
  [user list-entry]
  (let [abspath (.getAbsolutePath list-entry)
        stat    (.initializeObjStatForFile list-entry)]
    (hash-map
      :id            abspath
      :label         (ft/basename abspath)
      :permissions   (dataobject-perm-map user abspath)
      :date-created  (date-created-from-stat stat)
      :date-modified (date-mod-from-stat stat)
      :file-size     (size-from-stat stat))))

(defn list-dirs
  [user list-entries dirpath filter-files]
  (filterv
    #(and (get-in %1 [:permissions :read])
          (not (contains? filter-files (:id %1)))
          (not (contains? filter-files (:label %1))))
    (map #(dir-map-entry user %1) list-entries)))

(defn list-files
  [user list-entries dirpath filter-files]
  (filterv
    #(and (get-in %1 [:permissions :read]) 
          (not (contains? filter-files (:id %1)))
          (not (contains? filter-files (:label %1)))) 
    (map #(file-map-entry user %1) list-entries)))

(defn list-in-dir
  [fixed-path]
  (let [ffilter (proxy [java.io.FileFilter] [] (accept [stuff] true))] 
    (.getListInDirWithFileFilter 
      (:fileSystemAO cm) 
      (file fixed-path) 
      ffilter)))

(defn partition-files-folders
  [fixed-path]
  (group-by 
    #(.isDirectory %1)
    (list-in-dir fixed-path)))

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
     (with-jargon
       (validators/user-exists user)
       (validators/path-exists path)

       (when (and set-own? (not (owns? user path)))
         (set-permissions user path false false true))
       
       (validators/path-readable user path)
       
       (let [fixed-path   (ft/rm-last-slash path)
             ff           (set filter-files)
             parted-files (partition-files-folders fixed-path)
             dir-entries  (or (get parted-files true) (vector))
             file-entries (or (get parted-files false) (vector))
             dirs         (list-dirs user dir-entries fixed-path ff)
             add-files    #(if include-files
                             (assoc %1 :files (list-files user file-entries fixed-path ff))
                             %1)]
         (-> {}
           (assoc
             :id path
             :label         (ft/basename path)
             :hasSubDirs    true
             :date-created  (created-date path)
             :date-modified (lastmod-date path)
             :permissions   (collection-perm-map user path)
             :folders       dirs)
           add-files)))))

(defn root-listing
  ([user root-path]
     (root-listing user root-path (ft/basename root-path)))
  
  ([user root-path label]
     (root-listing user root-path label false))
  
  ([user root-path label set-own?]
    (with-jargon
      (validators/user-exists user)
      (validators/path-exists root-path)

      (when (and set-own? (not (owns? user root-path)))
        (set-permissions user root-path false false true))

      (validators/path-readable user root-path)
      (dir-map-entry user (file root-path) label))))

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
  (with-jargon
    (validators/user-exists user)
    (validators/path-writeable user (ft/dirname path))
    (validators/path-not-exists path)
    (mkdir path)
    (set-owner path user)
    (fix-owners path user @clj-jargon.jargon/username)
    {:path path 
     :permissions (collection-perm-map user path)}))

(defn- del
  "Performs some validation and calls delete.

   Parameters:
     user - username of the user requesting the directory deletion.
     paths - a sequence of strings containing directory paths.

   Returns a map describing the success or failure of the deletion command."
  [user paths type-func? type-error]
  (let [home-matcher #(= (str "/" @zone "/home/" user)
                         (ft/rm-last-slash %1))] 
    (with-jargon
      (validators/user-exists user)
      (validators/all-paths-exist paths)
      (validators/all-paths-writeable user paths)
      (validators/paths-satisfy-predicate paths type-func? type-error)
      
      (when (some true? (mapv home-matcher paths))
        (throw+ {:error_code ERR_NOT_AUTHORIZED 
                 :paths (filterv home-matcher paths)}))
      
      (doseq [p paths] 
        (delete p))
      
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
  (with-jargon
    (let [path-list  (conj sources dest)
          dest-paths (mapv #(ft/path-join dest (ft/basename %)) sources)
          types?     (every? true? (map type-func? sources))]
      (validators/user-exists user)
      (validators/all-paths-exist sources)
      (validators/all-paths-exist [dest])
      (validators/path-is-dir dest)
      (validators/all-paths-writeable user sources)
      (validators/path-writeable user dest)
      (validators/no-paths-exist dest-paths)
      (validators/paths-satisfy-predicate sources type-func? type-error)
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
    (validators/user-exists user)
    (validators/path-exists source)
    (validators/path-writeable user source)
    (validators/path-satisfies-predicate source type-func? type-error)
    (validators/path-not-exists dest)
    
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
  [path size]
  (let [realsize (file-size path)
        buffsize (if (<= realsize size) realsize size)
        buff     (char-array buffsize)]
    (read-file path buff)
    (.append (StringBuilder.) buff)))

(defn preview
  "Grabs a preview of a file in iRODS.

   Parameters:
     user - The username of the user requesting the preview.
     path - The path to the file in iRODS that will be previewed.
     size - The size (in bytes) of the preview to be created."
  [user path size]
  (with-jargon
    (log/debug (str "preview " user " " path " " size))
    (validators/user-exists user)
    (validators/path-exists path)
    (validators/path-readable user path)
    (validators/path-is-file path)
    
    (if (zero? (file-size path))
      ""
      (str (preview-buffer path size)))))

(defn user-home-dir
  ([user]
     (ft/path-join "/" (:zone cm) "home" user))
  ([staging-dir user set-owner?]
     (with-jargon
       (validators/user-exists user)
       
       (let [user-home (ft/path-join staging-dir user)]
         (if (not (exists? user-home))
           (mkdirs user-home))
         user-home))))

(defn metadata-get
  [user path]
  (with-jargon
    (validators/user-exists user)
    (validators/path-exists path)
    (validators/path-readable user path)
    
    (let [fix-unit #(if (= (:unit %1) IPCRESERVED) (assoc %1 :unit "") %1)
          avu      (map fix-unit (get-metadata (ft/rm-last-slash path)))]
      {:metadata avu})))

(defn get-tree
  [user path]
  (with-jargon
    (validators/user-exists user)
    (validators/path-exists path)
    (validators/path-readable user path)
    
    (let [value (:value (first (get-attribute path "tree-urls")))]
      (log/warn value)
      (json/read-json value))))

(defn metadata-set
  [user path avu-map]
  (with-jargon
    (validators/user-exists user)
    
    (when (= "failure" (:status avu-map))
      (throw+ {:error_code ERR_INVALID_JSON}))
    
    (validators/path-exists path)
    (validators/path-writeable user path)
    
    (let [new-unit (if (string/blank? (:unit avu-map))
                     IPCRESERVED
                     (:unit avu-map))]
      (set-metadata
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
  [path attr]
  (let [{:keys [attr value unit]} (first (get-attribute path attr))]
    (set-metadata path attr (encode-str value) unit)))

(defn metadata-batch-set
  [user path adds-dels]
  (with-jargon
    (validators/user-exists user)
    (validators/path-exists path)
    (validators/path-writeable user path)
    
    (let [new-path (ft/rm-last-slash path)]
      (doseq [del (:delete adds-dels)]
        (when (attribute? new-path del)
          (workaround-delete new-path del)
          (delete-metadata new-path del)))
      
      (doseq [avu (:add adds-dels)]
        (let [new-unit (if (string/blank? (:unit avu)) 
                         IPCRESERVED 
                         (:unit avu))]
          (set-metadata new-path (:attr avu) (:value avu) new-unit)))
      {:path (ft/rm-last-slash path) :user user})))

(defn tree-urls-value
  [path]
  (-> (get-attribute path "tree-urls") first :value json/read-json))

(defn current-tree-urls-value
  [path]
  (if (attribute? path "tree-urls")
    (tree-urls-value path)
    []))

(defn add-tree-urls
  [curr-val tree-urls]
  (-> (conj curr-val tree-urls) flatten json/json-str))

(defn set-new-tree-urls
  [path tree-urls]
  (set-metadata
   path
   "tree-urls"
   (add-tree-urls (current-tree-urls-value path) tree-urls)
   ""))

(defn set-tree
  [user path tree-urls]
  (with-jargon
    (validators/user-exists user)
    (validators/path-exists path)
    (validators/path-writeable user path)
    (set-new-tree-urls path (:tree-urls tree-urls))
    {:path path :user user}))

(defn metadata-delete
  [user path attr]
  (with-jargon
    (validators/user-exists user)
    (validators/path-exists path)
    (validators/path-writeable user path)
    (workaround-delete path attr)
    (delete-metadata path attr)
    {:path path :user user}))

(defn path-exists?
  [path]
  (with-jargon (exists? path)))

(defn path-stat
  [path]
  (with-jargon
    (validators/path-exists path)
    (stat path)))

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
  [path]
  (.detect (Tika.) (input-stream path)))

(defn manifest
  [user path data-threshold]
  (with-jargon
    (validators/user-exists user)
    (validators/path-exists path)
    (validators/path-is-file path)
    (validators/path-readable user path)
    
    {:action "manifest"
     :content-type (content-type path)
     :tree-urls (format-tree-urls (get-attribute path "tree-urls"))
     :preview (preview-url user path)}))

(defn download-file
  [user file-path]
  (with-jargon
    (validators/user-exists user)
    (validators/path-exists file-path)
    (validators/path-readable user file-path)
    
    (if (zero? (file-size file-path))
      ""
      (input-stream file-path))))

(defn download
  [user filepaths]
  (with-jargon
    (validators/user-exists user)
    
    (let [cart-key   (str (System/currentTimeMillis))
          account    (:irodsAccount cm)]
      {:action "download"
       :status "success"
       :data
       {:user user
        :home (ft/path-join "/" @zone "home" user)
        :password (store-cart user cart-key filepaths)
        :host (.getHost account)
        :port (.getPort account)
        :zone (.getZone account)
        :defaultStorageResource (.getDefaultStorageResource account)
        :key cart-key}})))

(defn upload
  [user]
  (with-jargon
    (validators/user-exists user)
    
    (let [account (:irodsAccount cm)]
      {:action "upload"
       :status "success"
       :data
       {:user user
        :home (ft/path-join "/" @zone "home" user)
        :password (temp-password user)
        :host (.getHost account)
        :port (.getPort account)
        :zone (.getZone account)
        :defaultStorageResource (.getDefaultStorageResource account)
        :key (str (System/currentTimeMillis))}})))

(defn share
  [user share-withs fpaths perms]
  (with-jargon
    (validators/user-exists user)
    (validators/all-users-exist share-withs)
    (validators/all-paths-exist fpaths)
    
    (when-not (every? (partial owns? user) fpaths)
      (throw+ {:error_code ERR_NOT_OWNER
               :paths (filterv
                        (partial owns? user)
                        fpaths)
               :user user}))

    (doseq [share-with share-withs]
      (doseq [fpath fpaths]
        (let [read-perm  (:read perms)
              write-perm (:write perms)
              own-perm   (:own perms)
              base-dir   (ft/path-join "/" @zone)]
          
          ;;Parent directories need to be readable, otherwise
          ;;files and directories that are shared will be
          ;;orphaned in the UI.
          (loop [dir-path (ft/dirname fpath)]
            (when-not (= dir-path base-dir)
              (let [curr-perms (permissions share-with dir-path)
                    curr-read  (:read curr-perms)
                    curr-write (:write curr-perms)
                    curr-own   (:own curr-perms)]
                (set-permissions share-with dir-path true curr-write curr-own)
                (recur (ft/dirname dir-path)))))
          
          ;;Set the actual permissions on the file/directory.
          (set-permissions share-with fpath read-perm write-perm own-perm true)
          
          ;;If the shared item is a directory, then it needs the inheritance 
          ;;bit set. Otherwise, any files that are added to the directory will
          ;;not be shared.
          (when (is-dir? fpath)
            (.setAccessPermissionInherit
             (:collectionAO cm)
             @zone
             fpath
             true)))))
    
    {:user share-withs
     :path fpaths
     :permissions perms}))

(defn contains-accessible-obj?
  [user dpath]
  (some #(is-readable? user %1) (list-paths dpath)))

(defn contains-subdir?
  [dpath]
  (some is-dir? (list-paths dpath)))

(defn subdirs
  [dpath]
  (filter is-dir? (list-paths dpath)))

(defn some-subdirs-readable?
  [user parent-path]
  (some #(is-readable? user %1) (subdirs parent-path)))

(defn unshare
  "Allows 'user' to unshare file 'fpath' with user 'unshare-with'."
  [user unshare-withs fpaths]
  (with-jargon
    (validators/user-exists user)
    (validators/all-users-exist unshare-withs)
    (validators/all-paths-exist fpaths)
    (validators/user-owns-paths user fpaths)

    (doseq [unshare-with unshare-withs]
      (doseq [fpath fpaths]
        (let [base-dir    (ft/path-join "/" @zone)
              parent-path (ft/dirname fpath)]
          (remove-permissions unshare-with fpath)
          
          (when-not (and (contains-subdir? parent-path)
                         (some-subdirs-readable? user parent-path))
            (loop [dir-path parent-path]
              (when-not (or (= dir-path base-dir)
                            (contains-accessible-obj? unshare-with dir-path))
                (remove-permissions unshare-with dir-path)
                (recur (ft/dirname dir-path)))))))))
  {:user unshare-withs
   :path fpaths})

(defn sharing-data
  [user root-dir inc-files filter-files]
  (list-dir user (ft/rm-last-slash root-dir) inc-files filter-files))

(defn shared-root-listing
  [user root-dir inc-files filter-files]
  
  (with-jargon
    (when-not (is-readable? user root-dir)
      (set-permissions user (ft/rm-last-slash root-dir) true false false))
    
    (assoc (sharing-data user root-dir inc-files filter-files)
      :label "Shared")))

(defn get-quota
  [user]
  (with-jargon
    (validators/user-exists user)
    (quota user)))

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
  (with-jargon
    (validators/user-exists user)
    (validators/path-exists path)
    (validators/path-writeable user path)

    (let [fully-restored (restoration-path user path name user-trash)]
      (validators/path-not-exists fully-restored)
      (validators/path-writeable user (ft/dirname fully-restored))
      (move path fully-restored)
      {:from path :to fully-restored})))

(defn copy-path
  [{:keys [user from to]}]
  (with-jargon
    (validators/user-exists user)
    (validators/all-paths-exist from)
    (validators/all-paths-readable user from)
    (validators/path-exists to)
    (validators/path-writeable user to)
    (validators/path-is-dir to)
    (validators/no-paths-exist (mapv #(ft/path-join to (ft/basename %)) from))

    (doseq [fr from]
      (copy fr to))
    
    {:from from :to to}))
