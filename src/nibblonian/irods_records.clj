(ns nibblonian.irods-records
  (:use [nibblonian.irods-base]
        [nibblonian.utils])
  (:require [clojure.contrib.logging :as log]
            [ring.util.codec :as codec]
            [clojure.contrib.base64 :as base64])
  (:import [org.irods.jargon.core.exception DataNotFoundException]
           [org.irods.jargon.core.protovalues FilePermissionEnum]
           [org.irods.jargon.core.pub.domain AvuData]))

(def read-perm FilePermissionEnum/READ)
(def write-perm FilePermissionEnum/WRITE)
(def own-perm FilePermissionEnum/OWN)
(def none-perm FilePermissionEnum/NONE)

(defrecord JargonCore [context-map]
  JARGON
  
  (user-groups
    [j user]
    (for [ug (. (:userGroupAO context-map) findUserGroupsForUser user)]
      (. ug getUserGroupName)))
  
  (user-dataobject-perms
    [j user data-path]
    (let [user-groups  (user-groups j user)
          zone         (:zone context-map)
          dataObjectAO (:dataObjectAO context-map)]
      (set 
        (into [] (filter 
                   (fn [perm] (not= perm none-perm)) 
                   (for [username user-groups]
                     (. dataObjectAO getPermissionForDataObject data-path username zone)))))))
  
  (user-collection-perms
    [j user coll-path]
    (let [user-groups  (user-groups j user)
          zone         (:zone context-map)
          collectionAO (:collectionAO context-map)]
      (set 
        (into [] (filter
                   (fn [perm] (not= perm none-perm))
                   (for [username user-groups]
                     (. collectionAO getPermissionForCollection coll-path username zone)))))))
  
  (dataobject-perm-map
    [j user data-path]
    "Returns a map of permission for the data-path"
    (log/debug (str "dataobject-perm-map " user " " data-path))
    (let [perms  (user-dataobject-perms j user data-path)
          read   (or (contains? perms read-perm) (contains? perms own-perm))
          write  (or (contains? perms write-perm) (contains? perms own-perm))]
      {:read  read
       :write write}))
  
  (collection-perm-map
    [j user coll-path]
    (log/debug (str "collection-perm-map " user " " coll-path))
    (let [perms  (user-collection-perms j user coll-path)
          read   (or (contains? perms read-perm) (contains? perms own-perm))
          write  (or (contains? perms write-perm) (contains? perms own-perm))]
      {:read  read
       :write write}))
  
  (dataobject-perm?
    [j username data-path checked-perm]
    (let [perms (user-dataobject-perms j username data-path)]
      (or (contains? perms checked-perm) (contains? perms own-perm))))
  
  (dataobject-readable?
    [j user data-path]
    (log/debug (str "dataobject-readable? " user " " data-path))
    (dataobject-perm? j user data-path read-perm))
  
  (dataobject-writeable?
    [j user data-path]
    (log/debug (str "dataobject-writeable? " user " " data-path))
    (dataobject-perm? j user data-path write-perm))
  
  (owns-dataobject?
    [j user data-path]
    (log/debug (str "owns-dataobject? " user " " data-path))
    (dataobject-perm? j user data-path own-perm))
  
  (collection-perm?
    [j username coll-path checked-perm]
    (let [perms (user-collection-perms j username coll-path)]
      (or (contains? perms checked-perm) (contains? perms own-perm))))
  
  (collection-readable?
    [j user coll-path]
    (log/debug (str "collection-readable? " user " " coll-path))
    (collection-perm? j user coll-path read-perm))
  
  (collection-writeable?
    [j user coll-path]
    (log/debug (str "collection-writeable? " user " " coll-path))
    (collection-perm? j user coll-path write-perm))
  
  (owns-collection?
    [j user coll-path]
    (log/debug (str "owns-collection? " user " " coll-path))
    (collection-perm? j user coll-path own-perm))
  
  (file
   [j path]
   "Returns an instance of IRODSFile representing 'path'. Note that path
    can point to either a file or a directory.

    Parameters:
      path - String containing a path.

    Returns: An instance of IRODSFile representing 'path'."
   (log/debug (str "file " path))
   (.  (:fileFactory context-map) (instanceIRODSFile path)))

  (exists?
   [j path]
   "Returns true if 'path' exists in iRODS and false otherwise.

    Parameters:
      path - String containing a path.

    Returns: true if the path exists in iRODS and false otherwise."
   (log/debug (str "exists? " path))
   (.. (file j path) exists))

  (paths-exist?
   [j paths]
   "Returns true if the paths exist in iRODS.

    Parameters:
      paths - A sequence of strings containing paths.

    Returns: Boolean"
   (log/debug (str "paths-exist? " paths))
   (== 0 (count (filter (fn [p] (not (exists? j p))) paths))))
  
  (is-file?
   [j path]
   "Returns true if the path is a file in iRODS, false otherwise."
   (log/debug (str "is-file? " path))
   (.. (. (:fileFactory context-map) (instanceIRODSFile path)) isFile))

  (is-dir?
   [j path]
   "Returns true if the path is a directory in iRODS, false otherwise."
   (let [ff (:fileFactory context-map)
	 fixed-path (rm-last-slash path)]
     (.. (. ff (instanceIRODSFile fixed-path)) isDirectory)))
  
  (data-object
   [j path]
   "Returns an instance of DataObject represeting 'path'."
   (log/debug (str "data-object " path))
   (. (:dataObjectAO context-map) findByAbsolutePath path))

  (collection
   [j path]
   "Returns an instance of Collection (the Jargon version) representing
    a directory in iRODS."
   (log/debug (str "collection " path))
   (. (:collectionAO context-map) findByAbsolutePath (rm-last-slash path)))

  (lastmod-date
   [j path]
   "Returns the date that the file/directory was last modified."
   (cond
    (is-dir? j path)  (str (long (. (. (collection j path) getModifiedAt) getTime)))
    (is-file? j path) (str (long (. (. (data-object j path) getUpdatedAt) getTime)))
    :else nil))

  (created-date
   [j path]
   "Returns the date that the file/directory was created."
   (cond
    (is-dir? j path)  (. (. (collection j path) getCreatedAt) toString)
    (is-file? j path) (. (. (data-object j path) getUpdatedAt) toString)
    :else             nil))

  (file-size
   [j path]
   "Returns the size of the file in bytes."
   (. (data-object j path) getDataSize))

  (response-map
   [j action paths]
   {:action action :paths paths})

  (user-exists?
   [j user]
   "Returns true if 'user' exists in iRODS."
   (log/debug (str  "user-exists? " user))
   (try
     (do (. (:userAO context-map) findByName user) true)
     (catch DataNotFoundException d false)))

  (set-owner
   [j path owner]
   "Sets the owner of 'path' to the username 'owner'.

    Parameters:
      path - The path whose owner is being set.
      owner - The username of the user who will be the owner of 'path'."
   (log/debug (str "set-owner " path " " owner))
   (if (is-file? j path)
     (. (:dataObjectAO context-map) setAccessPermissionOwn @zone path owner)
     (if (is-dir? j path)
       (. (:collectionAO context-map) setAccessPermissionOwn @zone path owner true))))

  (set-inherits
   [j path]
   "Sets the inheritance attribute of a collection to true.

    Parameters:
      path - The path being altered."
  (log/debug (str "set-inherits " path))
  (if (is-dir? j path)
    (. (:collectionAO context-map) setAccessPermissionInherit @zone path false)))

  (is-writeable?
   [j user path]
   "Returns true if 'user' can write to 'path'.

    Parameters:
      user - String containign a username.
      path - String containing an absolute path for something in iRODS."
   (log/debug (str "is-writeable? " user " " path))
   (cond
     (not (user-exists? j user)) false
     (is-dir? j path)            (collection-writeable? j user (. path replaceAll "/$" ""))
     (is-file? j path)           (dataobject-writeable? j user (. path replaceAll "/$" ""))
     :else                       false))

  (is-readable?
   [j user path]
   "Returns true if 'user' can read 'path'.

    Parameters:
      user - String containing a username.
      path - String containing an path for something in iRODS."
   (log/debug (str "is-readable? " user " " path))
   (cond
     (not (user-exists? j user)) false
     (is-dir? j path)            (collection-readable? j user (. path replaceAll "/$" ""))
     (is-file? j path)           (dataobject-readable? j user (. path replaceAll "/$" ""))
     :else                       false))

  (last-dir-in-path
   [j path]
   "Returns the name of the last directory in 'path'.

    Please note that this function works by calling
    getCollectionLastPathComponent on a Collection instance and therefore
    hits iRODS every time you call it. Don't call this from within a loop.

    Parameters:
      path - String containing the path for an item in iRODS.

    Returns:
      String containing the name of the last directory in the path."
   (log/debug (str "last-dir-in-path " path))
   (. (. (:collectionAO context-map) findByAbsolutePath (rm-last-slash path)) getCollectionLastPathComponent))

  (sub-collections
   [j path]
   "Returns a sequence of Collections that reside directly in the directory
    refered to by 'path'.

    Parameters:
      path - String containing the path to a directory in iRODS.

    Returns:
      Sequence containing Collections (the Jargon kind) representing
      directories that reside under the directory represented by 'path'."
   (log/debug (str "sub-collections " path))
   (. (:lister context-map) listCollectionsUnderPath (rm-last-slash path) 0))

  (sub-collection-paths
   [j path]
   "Returns a sequence of string containing the paths for directories
    that live under 'path' in iRODS.

    Parameters:
      path - String containing the path to a directory in iRODS.

    Returns:
      Sequence containing the paths for directories that live under 'path'."
  
   (log/debug (str "sub-collection-paths " path))
   (map
    (fn [s] (. s getFormattedAbsolutePath))
    (sub-collections j path)))

  (sub-dir-maps
   [j user list-obj]
   (let [abs-path (. list-obj getFormattedAbsolutePath)
	 lister   (:lister context-map)]
     {:id            abs-path
      :label         (basename abs-path)
      :permissions   (collection-perm-map j user abs-path)
      :hasSubDirs    (> (count (. lister listCollectionsUnderPath abs-path 0)) 0)
      :date-created  (str (long (. (. list-obj getCreatedAt) getTime)))
      :date-modified (str (long (. (. list-obj getModifiedAt) getTime)))}))

  (sub-file-maps
   [j user list-obj]
   (let [abs-path (. list-obj getFormattedAbsolutePath)]
     {:id            abs-path
      :label         (basename abs-path)
      :permissions   (dataobject-perm-map j user abs-path)
      :date-created  (str (long (. (. list-obj getCreatedAt) getTime)))
      :date-modified (str (long (. (. list-obj getModifiedAt) getTime)))
      :file-size     (str (. list-obj getDataSize))}))

  (paths-writeable?
   [j user paths]
   "Returns true if all of the paths in 'paths' are writeable by 'user'.

    Parameters:
      user - A string containing the username of the user requesting the check.
      paths - A sequence of strings containing the paths to be checked."
   (log/debug (str "paths-writeable? " user " " paths))
   (reduce (fn [f s] (and f s)) (map (fn [p] (is-writeable? j user p)) paths)))
  
  )

(defrecord JargonMetadata [context-map jc]
  METADATA

  (map2avu
   [m avu-map]
   "Converts an avu map into an AvuData instance."
   (AvuData/instance (:attr avu-map) (:value avu-map) (:unit avu-map)))

  (decode-metadata-value
   [m value]
   (try
     (java.lang.String. (codec/base64-decode value))
     (catch Exception e value)))
  
  (get-metadata
   [m dir-path]
   "Returns all of the metadata associated with a path." 
   (map
    (fn [mv]
      {:attr  (. mv getAvuAttribute)
       :value (. mv getAvuValue)
       :unit  (. mv getAvuUnit)})
    (if (is-dir? jc dir-path)
      (. (:collectionAO context-map) findMetadataValuesForCollection dir-path)
      (. (:dataObjectAO context-map) findMetadataValuesForDataObject dir-path))))
  
  (get-attribute
   [m dir-path attr]
   "Returns a list of avu maps for set of attributes associated with dir-path"
   (filter
    (fn [avu-map] 
      (= (:attr avu-map) attr))
    (get-metadata m dir-path)))

  (attribute?
   [m dir-path attr]
   "Returns true if the path has the associated attribute."
   (> (count (get-attribute m dir-path attr)) 0))

  (set-metadata
   [m dir-path attr value unit]
   "Sets an avu for dir-path."
   (let [avu    (AvuData/instance attr value unit)
         cao    (:collectionAO context-map)
         dao    (:dataObjectAO context-map)
         ao-obj (if (is-dir? jc dir-path) cao dao)]
     (if (== 0 (count (get-attribute m dir-path attr)))
       (. ao-obj addAVUMetadata dir-path avu)
       (let [old-avu (map2avu m (first (get-attribute m dir-path attr)))]
         (. ao-obj modifyAVUMetadata dir-path old-avu avu)))))
  
  (transform-map-for-deletion
   [m path avu-map]
   (let [new-map {:attr  (:attr avu-map)
                  :value (base64/encode-str (:value avu-map))
                  :unit  (:unit avu-map)}]
     (set-metadata m path (:attr new-map) (:value new-map) (:unit avu-map))
     new-map))
  
  (delete-metadata
   [m dir-path attr]
   "Deletes an avu from dir-path."
   (let [fattr (first (get-attribute m dir-path attr))
         avu (map2avu m (transform-map-for-deletion m dir-path fattr))
         cao (:collectionAO context-map)
         dao (:dataObjectAO context-map)
         ao-obj (if (is-dir? jc dir-path) cao dao)]
     (log/warn (str "Deleting AVU attribute " attr " for path " dir-path))
     (. ao-obj deleteAVUMetadata dir-path avu))))

(defn create-records []
  (let [c (create-jargon-context-map)
        j (JargonCore. c)
        m (JargonMetadata. c j)]
    {:context  c
     :jargon   j
     :metadata m}))