(ns nibblonian.jargon
  (:use [clojure-commons.file-utils])
  (:require [clojure.tools.logging :as log]
            [ring.util.codec :as codec]
            [clojure.data.codec.base64 :as base64])
  (:import [org.irods.jargon.core.exception DataNotFoundException]
           [org.irods.jargon.core.protovalues FilePermissionEnum]
           [org.irods.jargon.core.pub.domain AvuData]
           [org.irods.jargon.core.connection IRODSAccount]
           [org.irods.jargon.core.pub IRODSFileSystem]
           [org.irods.jargon.core.pub.io IRODSFileReader]
           [java.io FileInputStream]))

(def read-perm FilePermissionEnum/READ)
(def write-perm FilePermissionEnum/WRITE)
(def own-perm FilePermissionEnum/OWN)
(def none-perm FilePermissionEnum/NONE)

; Configuration settings for iRODS/Jargon
(def host (atom ""))
(def port (atom 0))
(def username (atom ""))
(def password (atom ""))
(def home (atom ""))
(def zone (atom ""))
(def defaultResource (atom ""))
(def irodsaccount (atom nil))
(def conn-map (atom nil))
(def fileSystem (atom nil))

;set up the thread-local var
(def ^:dynamic cm nil)

(defn init
  "Resets the connection config atoms with the values passed in."
  [ahost aport auser apass ahome azone ares]
  (log/debug "Setting iRODS configuration settings.")

  (reset! host ahost)
  (log/debug (str "Set host to " ahost))

  (reset! port aport)
  (log/debug (str "Set port to " aport))
  
  (reset! username auser)
  (log/debug (str "Set username to " auser))
  
  (reset! password apass)
  (log/debug (str "Set password to...haha, fooled you. I'm not logging that."))
  
  (reset! home ahome)
  (log/debug (str "Set home to " ahome))
  
  (reset! zone azone)
  (log/debug (str "Set zone to " azone))
  
  (reset! defaultResource ares)
  (log/debug (str "Set defaultResource to " ares)))

(defn clean-return
  [retval]
  (. (:fileSystem cm) close)
  retval)

(defn create-jargon-context-map
  []
  (let [account     (IRODSAccount. @host (Integer/parseInt @port) @username @password @home @zone @defaultResource)
        file-system (. IRODSFileSystem instance)
        aof         (. file-system getIRODSAccessObjectFactory)
        cao         (. aof getCollectionAO account)
        dao         (. aof getDataObjectAO account)
        uao         (. aof getUserAO account)
        ugao        (. aof getUserGroupAO account)
        ff          (. file-system getIRODSFileFactory account)
        fao         (. aof getIRODSFileSystemAO account)
        lister      (. aof getCollectionAndDataObjectListAndSearchAO account)]
    {:irodsAccount        account
     :fileSystem          file-system
     :accessObjectFactory aof
     :collectionAO        cao
     :dataObjectAO        dao
     :userAO              uao
     :userGroupAO         ugao
     :fileFactory         ff
     :fileSystemAO        fao
     :lister              lister
     :home                @home
     :zone                @zone}))

(defn user-groups
  [user]
  (for [ug (. (:userGroupAO cm) findUserGroupsForUser user)]
    (. ug getUserGroupName)))

(defn user-dataobject-perms
  [user data-path]
  (let [user-groups  (user-groups user)
        zone         (:zone cm)
        dataObjectAO (:dataObjectAO cm)]
      (set (into [] 
                 (filter 
                   (fn [perm] (not= perm none-perm)) 
                   (for [username user-groups]
                     (. dataObjectAO getPermissionForDataObject data-path username zone)))))))

(defn user-collection-perms
  [user coll-path]
  (let [user-groups  (user-groups user)
        zone         (:zone cm)
        collectionAO (:collectionAO cm)]
    (set 
      (into [] 
            (filter
              (fn [perm] (not= perm none-perm))
              (for [username user-groups]
                (. collectionAO getPermissionForCollection coll-path username zone)))))))

(defn dataobject-perm-map
  [user data-path]
  (let [perms  (user-dataobject-perms user data-path)
        read   (or (contains? perms read-perm) (contains? perms own-perm))
        write  (or (contains? perms write-perm) (contains? perms own-perm))]
    {:read  read
     :write write}))

(defn collection-perm-map
  [user coll-path]
  (let [perms  (user-collection-perms user coll-path)
        read   (or (contains? perms read-perm) (contains? perms own-perm))
        write  (or (contains? perms write-perm) (contains? perms own-perm))]
    {:read  read
     :write write}))

(defn dataobject-perm?
  [username data-path checked-perm]
  (let [perms (user-dataobject-perms username data-path)]
    (or (contains? perms checked-perm) (contains? perms own-perm))))

(defn dataobject-readable?
  [user data-path]
  (dataobject-perm? user data-path read-perm))
  
(defn dataobject-writeable?
  [user data-path]
  (dataobject-perm? user data-path write-perm))

(defn owns-dataobject?
  [user data-path]
  (dataobject-perm? user data-path own-perm))

(defn collection-perm?
  [username coll-path checked-perm]
  (let [perms (user-collection-perms username coll-path)]
    (or (contains? perms checked-perm) (contains? perms own-perm))))

(defn collection-readable?
  [user coll-path]
  (collection-perm? user coll-path read-perm))

(defn collection-writeable?
  [user coll-path]
  (collection-perm? user coll-path write-perm))

(defn owns-collection?
  [user coll-path]
  (collection-perm? user coll-path own-perm))

(defn file
  [path]
  "Returns an instance of IRODSFile representing 'path'. Note that path
    can point to either a file or a directory.

    Parameters:
      path - String containing a path.

    Returns: An instance of IRODSFile representing 'path'."
  (log/debug (str "file " path))
  (.  (:fileFactory cm) (instanceIRODSFile path)))

(defn exists?
  [path]
  "Returns true if 'path' exists in iRODS and false otherwise.

    Parameters:
      path - String containing a path.

    Returns: true if the path exists in iRODS and false otherwise."
  (.. (file path) exists))

(defn paths-exist?
  [paths]
  "Returns true if the paths exist in iRODS.

    Parameters:
      paths - A sequence of strings containing paths.

    Returns: Boolean"
  (== 0 (count (for [path paths :when (not (exists? path))] path))))

(defn is-file?
  [path]
  "Returns true if the path is a file in iRODS, false otherwise."
  (.. (. (:fileFactory cm) (instanceIRODSFile path)) isFile))

(defn is-dir?
  [path]
  "Returns true if the path is a directory in iRODS, false otherwise."
  (let [ff (:fileFactory cm)
        fixed-path (rm-last-slash path)]
    (.. (. ff (instanceIRODSFile fixed-path)) isDirectory)))

(defn data-object
  [path]
  "Returns an instance of DataObject represeting 'path'."
  (. (:dataObjectAO cm) findByAbsolutePath path))

(defn collection
  [path]
  "Returns an instance of Collection (the Jargon version) representing
    a directory in iRODS."
  (. (:collectionAO cm) findByAbsolutePath (rm-last-slash path)))

(defn lastmod-date
  [path]
  "Returns the date that the file/directory was last modified."
  (cond
    (is-dir? path)  (str (long (. (. (collection path) getModifiedAt) getTime)))
    (is-file? path) (str (long (. (. (data-object path) getUpdatedAt) getTime)))
    :else nil))

(defn created-date
  [path]
  "Returns the date that the file/directory was created."
  (cond
    (is-dir? path)  (. (. (collection path) getCreatedAt) toString)
    (is-file? path) (. (. (data-object path) getUpdatedAt) toString)
    :else             nil))

(defn file-size
  [path]
  "Returns the size of the file in bytes."
  (. (data-object path) getDataSize))

(defn response-map
  [action paths]
  {:action action :paths paths})

(defn user-exists?
  [user]
  "Returns true if 'user' exists in iRODS."
  (try
    (do (. (:userAO cm) findByName user) true)
    (catch DataNotFoundException d false)))

(defn set-owner
  [path owner]
  "Sets the owner of 'path' to the username 'owner'.

    Parameters:
      path - The path whose owner is being set.
      owner - The username of the user who will be the owner of 'path'."
  (if (is-file? path)
    (. (:dataObjectAO cm) setAccessPermissionOwn @zone path owner)
    (if (is-dir? path)
      (. (:collectionAO cm) setAccessPermissionOwn @zone path owner true))))

(defn set-inherits
  [path]
  "Sets the inheritance attribute of a collection to true.

    Parameters:
      path - The path being altered."
  (if (is-dir? path)
    (. (:collectionAO cm) setAccessPermissionInherit @zone path false)))

(defn is-writeable?
  [user path]
  "Returns true if 'user' can write to 'path'.

    Parameters:
      user - String containign a username.
      path - String containing an absolute path for something in iRODS."
  (cond
    (not (user-exists? user)) false
    (is-dir? path)            (collection-writeable? user (. path replaceAll "/$" ""))
    (is-file? path)           (dataobject-writeable? user (. path replaceAll "/$" ""))
    :else                       false))

(defn is-readable?
  [user path]
  "Returns true if 'user' can read 'path'.

    Parameters:
      user - String containing a username.
      path - String containing an path for something in iRODS."
  (cond
    (not (user-exists? user)) false
    (is-dir? path)            (collection-readable? user (. path replaceAll "/$" ""))
    (is-file? path)           (dataobject-readable? user (. path replaceAll "/$" ""))
    :else                       false))

(defn last-dir-in-path
  [path]
  "Returns the name of the last directory in 'path'.

    Please note that this function works by calling
    getCollectionLastPathComponent on a Collection instance and therefore
    hits iRODS every time you call it. Don't call this from within a loop.

    Parameters:
      path - String containing the path for an item in iRODS.

    Returns:
      String containing the name of the last directory in the path."
  (. (. (:collectionAO cm) findByAbsolutePath (rm-last-slash path)) getCollectionLastPathComponent))

(defn sub-collections
  [path]
  "Returns a sequence of Collections that reside directly in the directory
    refered to by 'path'.

    Parameters:
      path - String containing the path to a directory in iRODS.

    Returns:
      Sequence containing Collections (the Jargon kind) representing
      directories that reside under the directory represented by 'path'."
  (. (:lister cm) listCollectionsUnderPath (rm-last-slash path) 0))

(defn sub-collection-paths
  [path]
  "Returns a sequence of string containing the paths for directories
    that live under 'path' in iRODS.

    Parameters:
      path - String containing the path to a directory in iRODS.

    Returns:
      Sequence containing the paths for directories that live under 'path'."
  (map
    (fn [s] (. s getFormattedAbsolutePath))
    (sub-collections path)))

(defn sub-dir-maps
  [user list-obj]
  (let [abs-path (. list-obj getFormattedAbsolutePath)
        lister   (:lister cm)]
    {:id            abs-path
     :label         (basename abs-path)
     :permissions   (collection-perm-map user abs-path)
     :hasSubDirs    (> (count (. lister listCollectionsUnderPath abs-path 0)) 0)
     :date-created  (str (long (. (. list-obj getCreatedAt) getTime)))
     :date-modified (str (long (. (. list-obj getModifiedAt) getTime)))}))

(defn sub-file-maps
  [user list-obj]
  (let [abs-path (. list-obj getFormattedAbsolutePath)]
    {:id            abs-path
     :label         (basename abs-path)
     :permissions   (dataobject-perm-map user abs-path)
     :date-created  (str (long (. (. list-obj getCreatedAt) getTime)))
     :date-modified (str (long (. (. list-obj getModifiedAt) getTime)))
     :file-size     (str (. list-obj getDataSize))}))

(defn paths-writeable?
  [user paths]
  "Returns true if all of the paths in 'paths' are writeable by 'user'.

    Parameters:
      user - A string containing the username of the user requesting the check.
      paths - A sequence of strings containing the paths to be checked."
  (reduce (fn [f s] (and f s)) (map (fn [p] (is-writeable? user p)) paths)))

;;Metadata

(defn map2avu
  [avu-map]
  "Converts an avu map into an AvuData instance."
  (AvuData/instance (:attr avu-map) (:value avu-map) (:unit avu-map)))

(defn decode-metadata-value
  [value]
  (try
    (java.lang.String. (codec/base64-decode value))
    (catch Exception e value)))

(defn get-metadata
  [dir-path]
  "Returns all of the metadata associated with a path." 
  (map
    (fn [mv]
      {:attr  (. mv getAvuAttribute)
       :value (. mv getAvuValue)
       :unit  (. mv getAvuUnit)})
    (if (is-dir? dir-path)
      (. (:collectionAO cm) findMetadataValuesForCollection dir-path)
      (. (:dataObjectAO cm) findMetadataValuesForDataObject dir-path))))

(defn get-attribute
  [dir-path attr]
  "Returns a list of avu maps for set of attributes associated with dir-path"
  (filter
    (fn [avu-map] 
      (= (:attr avu-map) attr))
    (get-metadata dir-path)))

(defn attribute?
  [dir-path attr]
  "Returns true if the path has the associated attribute."
  (> (count (get-attribute dir-path attr)) 0))

(defn set-metadata
  [dir-path attr value unit]
  "Sets an avu for dir-path."
  (let [avu    (AvuData/instance attr value unit)
        cao    (:collectionAO cm)
        dao    (:dataObjectAO cm)
        ao-obj (if (is-dir? dir-path) cao dao)]
    (if (== 0 (count (get-attribute dir-path attr)))
      (. ao-obj addAVUMetadata dir-path avu)
      (let [old-avu (map2avu (first (get-attribute dir-path attr)))]
        (. ao-obj modifyAVUMetadata dir-path old-avu avu)))))

(defn transform-map-for-deletion
  [path avu-map]
  (let [new-map {:attr  (:attr avu-map)
                 :value (base64/encode (bytes (:value avu-map)))
                 :unit  (:unit avu-map)}]
    (set-metadata path (:attr new-map) (:value new-map) (:unit avu-map))
    new-map))

(defn delete-metadata
  [dir-path attr]
  "Deletes an avu from dir-path."
  (let [fattr (first (get-attribute dir-path attr))
        avu (map2avu (transform-map-for-deletion dir-path fattr))
        cao (:collectionAO cm)
        dao (:dataObjectAO cm)
        ao-obj (if (is-dir? dir-path) cao dao)]
    (log/warn (str "Deleting AVU attribute " attr " for path " dir-path))
    (. ao-obj deleteAVUMetadata dir-path avu)))

(defn list-all
  [dir-path]
  (let [lister (:lister cm)]
    (. lister listDataObjectsAndCollectionsUnderPath dir-path)))

(defn mkdir
  [dir-path]
  (let [fileSystemAO (:fileSystemAO cm)]
    (. fileSystemAO mkdir (file dir-path) true)))

(defn mkdirs
  [dir-path]
  (. (file dir-path) mkdirs))

(defn delete
  [a-path]
  (let [fileSystemAO (:fileSystemAO cm)]
    (if (is-dir? a-path)
      (. fileSystemAO directoryDeleteForce (file a-path))
      (. fileSystemAO fileDeleteForce (file a-path)))))

(defn move
  [source dest]
  (let [fileSystemAO (:fileSystemAO cm)
        src          (file source)
        dst          (file (path-join source dest))]
    (if (is-file? source)
      (. fileSystemAO renameFile src dst)
      (. fileSystemAO renameDirectory src dst))))

(defn move-all
  [sources dest]
  (into [] (map (fn [src] (move src dest)) sources)))

(defn output-stream
  "Returns an FileOutputStream for a file in iRODS pointed to by 'output-path'."
  [output-path]
  (let [fileFactory (:fileFactory cm)]
    (. fileFactory instanceIRODSFileOutputStream (file output-path))))

(defn input-stream
  "Returns a FileInputStream for a file in iRODS pointed to by 'input-path'"
  [input-path]
  (let [fileFactory (:fileFactory cm)]
    (. fileFactory instanceIRODSFileInputStream (file input-path))))

(defn read-file
  [fpath buffer]
  (let [fileFactory (:fileFactory cm)
        read-file   (file fpath)]
    (. (IRODSFileReader. read-file fileFactory) read buffer)))

(defmacro with-jargon
  [& body]
  `(let [context# (create-jargon-context-map)]
     (binding
       [cm context#]
       (clean-return (do ~@body)))))
