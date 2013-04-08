(ns nibblonian.irods-actions
  (:require [cheshire.core :as cheshire]
            [clojure.tools.logging :as log]
            [clojure.java.io :as ds]
            [clojure.data.codec.base64 :as b64]
            [clojure.set :as set]
            [ring.util.codec :as cdc]
            [clojure-commons.file-utils :as ft]
            [clojure.string :as string]
            [nibblonian.validators :as validators]
            [nibblonian.riak :as riak])
  (:use [clj-jargon.jargon :exclude [init list-dir] :as jargon]
        clojure-commons.error-codes
        [nibblonian.config :exclude [init]]
        [slingshot.slingshot :only [try+ throw+]])
  (:import [org.apache.tika Tika]))

(def IPCRESERVED "ipc-reserved-unit")
(def IPCSYSTEM "ipc-system-avu")

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
  (let [filtered-users (set (conj (perms-filter) user (irods-user)))]
    (filter
     #(not (contains? filtered-users (:user %1)))
     (list-user-perms cm abspath))))

(defn- list-perm
  [cm user abspath]
  {:path abspath
   :user-permissions (filtered-user-perms cm user abspath)})

(defn list-perms
  [user abspaths]
  (with-jargon (jargon-config) [cm]
    (validators/user-exists cm user)
    (validators/all-paths-exist cm abspaths)
    (validators/user-owns-paths cm user abspaths)
    (mapv (partial list-perm cm user) abspaths)))

(defn date-mod-from-stat
  [stat]
  (str (long (.. stat getModifiedAt getTime))))

(defn date-created-from-stat
  [stat]
  (str (long (.. stat getCreatedAt getTime))))

(defn size-from-stat
  [stat]
  (str (.getObjSize stat)))

(defn sharing? [abs] (= (irods-home) abs))
(defn community? [abs] (= (community-data) abs))
(defn user-trash-dir
  ([user]
     (with-jargon (jargon-config) [cm]
       (user-trash-dir cm user)))
  ([cm user]
     (trash-base-dir cm user)))

(defn user-trash-dir?
  ([user path-to-check]
     (with-jargon (jargon-config) [cm]
       (user-trash-dir? cm user path-to-check)))
  ([cm user path-to-check]
     (= (ft/rm-last-slash path-to-check)
        (ft/rm-last-slash (user-trash-dir cm user)))))

(defn id->label
  "Generates a label given a listing ID (read as absolute path)."
  [cm user id]
  (cond
   (user-trash-dir? cm user id)
   "Trash"

   (sharing? (ft/add-trailing-slash id))
   "Shared With Me"

   (community? id)
   "Community Data"

   :else
   (ft/basename id)))

(defn dir-map-entry
  [cm user list-entry]
  (let [abspath (.getAbsolutePath list-entry)
        stat    (.initializeObjStatForFile list-entry)]
    (hash-map
     :id            abspath
     :label         (id->label cm user abspath)
     :permissions   (collection-perm-map cm user abspath)
     :hasSubDirs    true
     :date-created  (date-created-from-stat stat)
     :date-modified (date-mod-from-stat stat)
     :file-size     (size-from-stat stat))))

(defn list-in-dir
  [cm fixed-path]
  (let [ffilter (proxy [java.io.FileFilter] [] (accept [stuff] true))]
    (.getListInDirWithFileFilter
      (:fileSystemAO cm)
      (file cm fixed-path)
      ffilter)))

(defn string-contains?
  [container-str str-to-check]
  (pos? (count (set/intersection (set container-str) (set str-to-check)))))

(defn good-string?
  [str-to-check]
  (log/warn "filter-chars: " (filter-chars))
  (not (string-contains? (filter-chars) str-to-check)))

(defn valid-file-map? [map-to-check] (good-string? (:id map-to-check)))

(defn gen-listing
  [cm user path filter-files include-files]
  (let [fixed-path     (ft/rm-last-slash path)
        ff             (set filter-files)
        listing        (jargon/list-dir cm user path :include-files include-files)
        filter-listing (fn [l] (remove #(or (ff (:id %))
                                            (ff (:label %))
                                            (not (valid-file-map? %))) l))]
    (if include-files
      (assoc listing
        :folders (filter-listing (:folders listing))
        :files   (filter-listing (:files listing)))
      (assoc listing
        :folders (filter-listing (:folders listing))))))

(defn list-dir
  "A non-recursive listing of a directory. Contains entries for files.

   The map for the directory listing looks like this:
     {:id \"full path to the top-level directory\"
      :label \"basename of the path\"
      :files A sequence of file maps
      :folders A sequence of directory maps}

   Parameters:
     user - String containing the username of the user requesting the
        listing.
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

      (gen-listing cm user path filter-files include-files))))

(defn root-listing
  ([user root-path]
     (root-listing user root-path false))

  ([user root-path set-own?]
     (with-jargon (jargon-config) [cm]
       (validators/user-exists cm user)

       (when (and (= root-path (user-trash-dir cm user)) (not (exists? cm root-path)))
         (mkdir cm root-path)
         (set-permissions cm user root-path false false true))

       (validators/path-exists cm root-path)

       (when (and set-own? (not (owns? cm user root-path)))
         (set-permissions cm user root-path false false true))

       (when-let [res (jargon/list-dir cm user root-path :include-subdirs false)]
         (assoc res :label (id->label cm user (:id res)))))))

(defn create
  "Creates a directory at 'path' in iRODS and sets the user to 'user'.

   Parameters:
     user - String containing the username of the user requesting the
         directory.
     path - The path that the directory will be created at in iRODS.

   Returns a map of the format {:action \"create\" :path \"path\"}"
  [user path]
  (log/debug (str "create " user " " path))
  (with-jargon (jargon-config) [cm]
    (let [fixed-path (ft/rm-last-slash path)]
      (when-not (good-string? fixed-path)
        (throw+ {:error_code ERR_BAD_OR_MISSING_FIELD
                 :path path}))
      (validators/user-exists cm user)
      (validators/path-writeable cm user (ft/dirname fixed-path))
      (validators/path-not-exists cm fixed-path)

      (mkdir cm fixed-path)
      (set-owner cm fixed-path user)
      {:path fixed-path :permissions (collection-perm-map cm user fixed-path)})))

(defn source->dest
  [source-path dest-path]
  (ft/path-join dest-path (ft/basename source-path)))

(defn move-paths
  "Moves directories listed in 'sources' into the directory listed in 'dest'. This
   works by calling move and passing it move-dir."
  [user sources dest]
  (with-jargon (jargon-config) [cm]
    (let [path-list  (conj sources dest)
          all-paths  (apply merge (mapv #(hash-map (source->dest %1 dest) %1) sources))
          dest-paths (keys all-paths)]
      (validators/user-exists cm user)
      (validators/all-paths-exist cm sources)
      (validators/all-paths-exist cm [dest])
      (validators/path-is-dir cm dest)
      (validators/user-owns-paths cm user sources)
      (validators/path-writeable cm user dest)
      (validators/no-paths-exist cm dest-paths)
      (move-all cm sources dest :user user :admin-users (irods-admins))
      {:sources sources :dest dest})))

(defn rename-path
  "High-level file renaming. Calls rename-func, passing it file-rename as the mv-func param."
  [user source dest]
  (with-jargon (jargon-config) [cm]
    (validators/user-exists cm user)
    (validators/path-exists cm source)
    (validators/user-owns-path cm user source)
    (validators/path-not-exists cm dest)

    (let [result (move cm source dest :user user :admin-users (irods-admins))]
      (when-not (nil? result)
        (throw+ {:error_code ERR_INCOMPLETE_RENAME
                 :paths result
                 :user user}))
      {:source source :dest dest :user user})))

(defn- preview-buffer
  [cm path size]
  (let [realsize (file-size cm path)
        buffsize (if (<= realsize size) realsize size)
        buff     (char-array buffsize)]
    (read-file cm path buff)
    (.append (StringBuilder.) buff)))

(defn gen-preview
  [cm path size]
  (if (zero? (file-size cm path))
    ""
    (str (preview-buffer cm path size))))

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
    (gen-preview cm path size)))

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

(defn fix-unit
  [avu]
  (if (= (:unit avu) IPCRESERVED)
    (assoc avu :unit "")
    avu))

(defn list-path-metadata
  [cm path]
  (filterv
   #(not= (:unit %) IPCSYSTEM)
   (map fix-unit (get-metadata cm (ft/rm-last-slash path)))))

(defn list-user-groups
  [user]
  "Returns a list of names for the groups a user is in.

   Parameters:
     user - the user's iRODS account name

   Returns:
     A list of group names

   Preconditions:
     clj-jargon must have been initialized

   Throws:
     ERR_NOT_A_USER - This is thrown if user is not a valid iRODS account name."
  (with-jargon (jargon-config) [cm]
    (validators/user-exists cm user)
    (user-groups cm user)))

(defn metadata-get
  [user path]
  (with-jargon (jargon-config) [cm]
    (validators/user-exists cm user)
    (validators/path-exists cm path)
    (validators/path-readable cm user path)
    {:metadata (list-path-metadata cm path)}))

(defn metadata-set
  [user path avu-map]
  (with-jargon (jargon-config) [cm]
    (validators/user-exists cm user)

    (when (= "failure" (:status avu-map))
      (throw+ {:error_code ERR_INVALID_JSON}))

    (validators/path-exists cm path)
    (validators/path-writeable cm user path)

    (let [new-unit (if (string/blank? (:unit avu-map)) IPCRESERVED (:unit avu-map))]
      (set-metadata cm (ft/rm-last-slash path) (:attr avu-map) (:value avu-map) new-unit)
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
  (let [{:keys [attr value unit]} (first (get-attribute cm path attr))]
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
        (let [new-unit (if (string/blank? (:unit avu)) IPCRESERVED (:unit avu))]
          (set-metadata cm new-path (:attr avu) (:value avu) new-unit)))
      {:path (ft/rm-last-slash path) :user user})))

(defn metadata-delete
  [user path attr]
  (with-jargon (jargon-config) [cm]
    (validators/user-exists cm user)
    (validators/path-exists cm path)
    (validators/path-writeable cm user path)

    (let [fix-unit #(if (= (:unit %1) IPCRESERVED) (assoc %1 :unit "") %1)
          avu      (map fix-unit (get-metadata cm (ft/rm-last-slash path)))]
      (workaround-delete cm path attr)
      (delete-metadata cm path attr))
    {:path path :user user}))

(defn path-exists? [user path] (with-jargon (jargon-config) [cm] (exists? cm path)))

(defn list-user-perms-for-path
  [cm user path]
  (if (is-file? cm path)
    (.listPermissionsForDataObject (:dataObjectAO cm) path)
    (.listPermissionsForCollection (:collectionAO cm) path)))

(defn count-shares
  [cm user path]
  (let [filter-users (set (conj (perms-filter) user (irods-user)))
        full-listing (list-user-perms-for-path cm user path)]
    (count
     (filterv
      #(not (contains? filter-users (.getUserName %1)))
      full-listing))))

(defn merge-counts
  [stat-map cm path]
  (if (is-dir? cm path)
    (let [subs (group-by #(is-dir? cm %) (mapv #(.getAbsolutePath %) (list-in-dir cm path)))]
      (merge stat-map {:file-count (count (get subs false))
                      :dir-count  (count (get subs true))}))
    stat-map))

(defn merge-shares
  [stat-map cm user path]
  (if (owns? cm user path)
    (merge stat-map {:share-count (count-shares cm user path)})
    stat-map))

(defn path-stat
  [user path]
  (with-jargon (jargon-config) [cm]
    (validators/path-exists cm path)
    (-> (stat cm path)
        (merge {:permissions (permissions cm user path)})
        (merge-shares cm user path)
        (merge-counts cm path))))

(defn- format-tree-urls
  [treeurl-maps]
  (if (pos? (count treeurl-maps))
    (cheshire/decode (:value (first (seq treeurl-maps))) true)
    []))

(defn preview-url
  [user path]
  (str "file/preview?user=" (cdc/url-encode user) "&path=" (cdc/url-encode path)))

(defn content-type
  [cm path]
  (.detect (Tika.) (input-stream cm path)))

(defn extract-tree-urls
  [cm fpath]
  (if (attribute? cm fpath "tree-urls")
    (-> (get-attribute cm fpath "tree-urls")
        first
        :value
        riak/get-tree-urls
        (cheshire/decode true)
        :tree-urls)
    []))

(defn manifest
  [user path data-threshold]
  (with-jargon (jargon-config) [cm]
    (validators/user-exists cm user)
    (validators/path-exists cm path)
    (validators/path-is-file cm path)
    (validators/path-readable cm user path)

    {:action       "manifest"
     :content-type (content-type cm path)
     :tree-urls    (extract-tree-urls cm path)
     :preview      (preview-url user path)}))

(defn download-file
  [user file-path]
  (with-jargon (jargon-config) [cm]
    (validators/user-exists cm user)
    (validators/path-exists cm file-path)
    (validators/path-readable cm user file-path)

    (if (zero? (file-size cm file-path)) "" (input-stream cm file-path))))

(defn download
  [user filepaths]
  (with-jargon (jargon-config) [cm]
    (validators/user-exists cm user)

    (let [cart-key (str (System/currentTimeMillis))
          account  (:irodsAccount cm)]
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

(def shared-with-attr "ipc-contains-obj-shared-with")

(defn delete-avu
  "Deletes the provided AVU from the path."
  [cm fpath avu-map]
  (.deleteAVUMetadata (:collectionAO cm) fpath (map2avu avu-map)))

(defn add-user-shared-with
  "Adds 'ipc-contains-obj-shared-with' AVU for a user to an object if it's not there."
  [cm fpath shared-with]
  (when (empty? (get-avus-by-collection cm fpath shared-with shared-with-attr))
    (set-metadata cm fpath shared-with shared-with shared-with-attr)))

(defn remove-user-shared-with
  "Removes 'ipc-contains-obj-shared-with' AVU for a user from an object if it's there."
  [cm fpath shared-with]
  (when-not (empty? (get-avus-by-collection cm fpath shared-with shared-with-attr))
    (delete-metadata cm fpath shared-with)))

(defn shared?
  ([cm share-with fpath]
     (:read (permissions cm share-with fpath)))
  ([cm share-with fpath desired-perms]
     (let [curr-perms (permissions cm share-with fpath)]
       (and (:read curr-perms) (= curr-perms desired-perms)))))

(defn- skip-share
  [user path reason]
  {:user    user
   :path    path
   :reason  reason
   :skipped true})

(defn- share-path
  "Shares a path with a user. This consists of the following steps:

       1. The parent directories up to the sharer's home directory need to be marked as readable
          by the sharee. Othwerwise, any files that are shared will be orphaned in the UI.

       2. If the shared item is a directory then the inherit bit needs to be set so that files
          that are uploaded into the directory will also be shared.

       3. The permissions are set on the item being shared. This is done recursively in case the
          item being shared is a directory."
  [cm share-with {read-perm :read write-perm :write own-perm :own :as perms} fpath]
  (let [base-dirs #{(ft/rm-last-slash (:home cm)) (trash-base-dir cm)}]
    (process-parent-dirs (partial set-readable cm share-with true) #(not (base-dirs %)) fpath))
  (when (is-dir? cm fpath)
    (.setAccessPermissionInherit (:collectionAO cm) (:zone cm) fpath true))
  (set-permissions cm share-with fpath read-perm write-perm own-perm true)
  {:user share-with :path fpath})

(defn- share-paths
  [cm user share-withs fpaths perms]
  (for [share-with share-withs
        fpath      fpaths]
    (cond (= user share-with)                 (skip-share share-with fpath :share-with-self)
          (shared? cm share-with fpath perms) (skip-share share-with fpath :already-shared)
          :else                               (share-path cm share-with perms fpath))))

(defn share
  [user share-withs fpaths perms]
  (with-jargon (jargon-config) [cm]
    (validators/user-exists cm user)
    (validators/all-users-exist cm share-withs)
    (validators/all-paths-exist cm fpaths)
    (validators/user-owns-paths cm user fpaths)

    (let [keyfn      #(if (:skipped %) :skipped :succeeded)
          share-recs (group-by keyfn (share-paths cm user share-withs fpaths perms))
          sharees    (map :user (:succeeded share-recs))
          home-dir   (ft/path-join (:home cm) user)]
      (dorun (map (partial add-user-shared-with cm (ft/path-join (:home cm) user)) sharees))
      {:user        sharees
       :path        fpaths
       :skipped     (map #(dissoc % :skipped) (:skipped share-recs))
       :permissions perms})))

(defn contains-subdir?
  [cm dpath]
  (some #(is-dir? cm %) (list-paths cm dpath)))

(defn subdirs
  [cm dpath]
  (filter #(is-dir? cm %) (list-paths cm dpath)))

(defn some-subdirs-readable?
  [cm user parent-path]
  (some #(is-readable? cm user %1) (subdirs cm parent-path)))

(defn- remove-inherit-bit?
  [cm user fpath]
  (empty? (remove (comp (conj (irods-admins) user) :user)
                  (list-user-perms cm fpath))))

(defn- unshare-dir
  "Removes the inherit bit from a directory if the directory is no longer shared with any accounts
   other than iRODS administrative accounts."
  [cm user unshare-with fpath]
  (when (remove-inherit-bit? cm user fpath)
    (.setAccessPermissionToNotInherit (:collectionAO cm) (:zone cm) fpath true)))

(defn- unshare-path
  "Removes permissions for a user to access a path.  This consists of several steps:

       1. Remove the access permissions for the user.  This is done recursively in case the path
          being unshared is a directory.

       2. If the item being unshared is a directory, perform any directory-specific unsharing
          steps that are required.

       3. Remove the user's read permissions for parent directories in which the user no longer has
          access to any other files or subdirectories."
  [cm user unshare-with fpath]
  (let [base-dirs #{(ft/rm-last-slash (:home cm)) (trash-base-dir cm)}]
    (remove-permissions cm unshare-with fpath)
    (when (is-dir? cm fpath)
      (unshare-dir cm user unshare-with fpath))
    (process-parent-dirs (partial set-readable cm unshare-with false)
                         #(and (not (base-dirs %))
                               (not (contains-accessible-obj? cm unshare-with %)))
                         fpath)
    {:user unshare-with :path fpath}))

(defn- unshare-paths
  [cm user unshare-withs fpaths]
  (for [unshare-with unshare-withs
        fpath        fpaths]
    (cond (= user unshare-with)           (skip-share unshare-with fpath :unshare-with-self)
          (shared? cm unshare-with fpath) (unshare-path cm user unshare-with fpath)
          :else                           (skip-share unshare-with fpath :not-shared))))

(defn clean-up-unsharee-avus
  [cm fpath unshare-with]
  (when-not (shared? cm unshare-with fpath)
    (remove-user-shared-with cm fpath unshare-with)))

(defn unshare
  "Allows 'user' to unshare file 'fpath' with user 'unshare-with'."
  [user unshare-withs fpaths]
  (log/debug "entered unshare")

  (with-jargon (jargon-config) [cm]
    (validators/user-exists cm user)
    (validators/all-users-exist cm unshare-withs)
    (validators/all-paths-exist cm fpaths)
    (validators/user-owns-paths cm user fpaths)

    (log/debug "unshare - after validators")
    (log/debug "unshare - user: " user)
    (log/debug "unshare - unshare-withs: " unshare-withs)
    (log/debug "unshare - fpaths: " fpaths)

    (let [keyfn        #(if (:skipped %) :skipped :succeeded)
          unshare-recs (group-by keyfn (unshare-paths cm user unshare-withs fpaths))
          unsharees    (map :user (:succeeded unshare-recs))
          home-dir     (ft/path-join (:home cm) user)]
      (dorun (map (partial clean-up-unsharee-avus cm home-dir) unsharees))
      {:user unsharees
       :path fpaths
       :skipped (map #(dissoc % :skipped) (:skipped unshare-recs))})))

(defn list-of-homedirs-with-shared-files
  [cm user]
  (mapv
   #(let [stat (.getObjStat (:fileSystemAO cm) %1)]
      (hash-map
       :id            %1
       :label         (id->label cm user %1)
       :hasSubDirs    true
       :date-created  (date-created-from-stat stat)
       :date-modified (date-mod-from-stat stat)
       :permissions   (collection-perm-map cm user %1)
       :file-size     (size-from-stat stat)))
   (filterv
    #(is-readable? cm user %1)
    (list-collections-with-attr-units cm user shared-with-attr))))

(defn list-sharing
  [cm user path]
  (log/warn "entered list-sharing")
  (let [dirs (list-of-homedirs-with-shared-files cm user)]
    (hash-map
     :id            path
     :label         (id->label cm user path)
     :hasSubDirs    true
     :date-created  (created-date cm path)
     :date-modified (lastmod-date cm path)
     :permissions   (collection-perm-map cm user path)
     :folders       dirs)))

(defn sharing-data
  [cm user root-dir]
  (list-sharing cm user (ft/rm-last-slash root-dir)))

(defn shared-root-listing
  [user root-dir inc-files filter-files]

  (with-jargon (jargon-config) [cm]
    (when-not (is-readable? cm user root-dir)
      (set-permissions cm user (ft/rm-last-slash root-dir) true false false))

    (assoc (sharing-data cm user root-dir) :label "Shared")))

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
    (or (ft/dirname (string/replace-first path (ft/add-trailing-slash user-trash) ""))
        "")
    name)))

(defn user-trash
  [user]
  (with-jargon (jargon-config) [cm]
    (validators/user-exists cm user)
    {:trash (user-trash-dir cm user)}))

(def alphanums (concat (range 48 58) (range 65 91) (range 97 123)))

(defn rand-str
  [length]
  (apply str (take length (repeatedly #(char (rand-nth alphanums))))))

(defn randomized-trash-path
  [cm user path-to-inc]
  (ft/path-join
   (user-trash-dir cm user)
   (str (ft/basename path-to-inc) "." (rand-str 7))))

(defn move-to-trash
  [cm p user]
  (let [trash-path (randomized-trash-path cm user p)]
    (move cm p trash-path :user user :admin-users (irods-admins))
    (set-metadata cm trash-path "ipc-trash-origin" p IPCSYSTEM)))

(defn delete-paths
  "Performs some validation and calls delete.

   Parameters:
     user - username of the user requesting the directory deletion.
     paths - a sequence of strings containing directory paths.

   Returns a map describing the success or failure of the deletion command."
  [user paths]
  (let [home-matcher #(= (str "/" (irods-zone) "/home/" user)
                         (ft/rm-last-slash %1))]
    (with-jargon (jargon-config) [cm]
      (validators/user-exists cm user)
      (validators/all-paths-exist cm paths)
      (validators/user-owns-paths cm user paths)

      (when (some true? (mapv home-matcher paths))
        (throw+ {:error_code ERR_NOT_AUTHORIZED
                 :paths (filterv home-matcher paths)}))

      (doseq [p paths]
        (let [path-tickets (mapv :ticket-id (ticket-ids-for-path cm user p))]
          (doseq [path-ticket path-tickets]
            (delete-ticket cm user path-ticket)))

        (if-not (.startsWith p (user-trash-dir cm user))
          (move-to-trash cm p user)
          (delete cm p)))

      {:paths paths})))

(defn trash-origin-path
  [cm user p]
  (if (attribute? cm p "ipc-trash-origin")
    (:value (first (get-attribute cm p "ipc-trash-origin")))
    (ft/path-join (user-home-dir user) (ft/basename p))))

(defn restore-to-homedir?
  [cm p]
  (not (attribute? cm p "ipc-trash-origin")))

(defn restoration-path
  [cm user path]
  (let [user-home   (user-home-dir user)
        origin-path (trash-origin-path cm user path)
        inc-path    #(str origin-path "." %)]
    (if-not (exists? cm origin-path)
      origin-path
      (loop [attempts 0]
        (if (exists? cm (inc-path attempts))
          (recur (inc attempts))
          (inc-path attempts))))))

(defn restore-parent-dirs
  [cm user path]
  (log/warn "restore-parent-dirs")
  (log/warn (ft/dirname path))
  (when-not (exists? cm (ft/dirname path))
    (mkdirs cm (ft/dirname path))
    (log/warn "Created " (ft/dirname path))

    (loop [parent (ft/dirname path)]
      (when (and (not= parent (user-home-dir user)) (not (owns? cm user parent)))
        (log/warn (str "Restoring ownership of parent dir: " parent))
        (set-owner cm parent user)
        (recur (ft/dirname parent))))))

(defn restore-path
  [{:keys [user paths user-trash]}]
  (with-jargon (jargon-config) [cm]
    (validators/user-exists cm user)
    (validators/all-paths-exist cm paths)
    (validators/all-paths-writeable cm user paths)

    (let [retval (atom (hash-map))]
      (doseq [path paths]
        (let [fully-restored      (restoration-path cm user path)
              restored-to-homedir (restore-to-homedir? cm path)]
          (log/warn "Restoring " path " to " fully-restored)

          (validators/path-not-exists cm fully-restored)
          (log/warn fully-restored " does not exist. That's good.")

          (restore-parent-dirs cm user fully-restored)
          (log/warn "Done restoring parent dirs for " fully-restored)

          (validators/path-writeable cm user (ft/dirname fully-restored))
          (log/warn fully-restored "is writeable. That's good.")

          (log/warn "Moving " path " to " fully-restored)
          (validators/path-not-exists cm fully-restored)

          (log/warn fully-restored " does not exist. That's good.")
          (move cm path fully-restored :user user :admin-users (irods-admins))
          (log/warn "Done moving " path " to " fully-restored)

          (reset! retval
                  (assoc @retval path {:restored-path fully-restored
                                       :partial-restore restored-to-homedir}))))
      {:restored @retval})))

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
       (validators/no-paths-exist cm (mapv #(ft/path-join to (ft/basename %)) from))

       (when (some true? (mapv #(= to %1) from))
         (throw+ {:error_code ERR_INVALID_COPY
                  :paths (filterv #(= to %1) from)}))

       (doseq [fr from]
         (let [metapath (ft/rm-last-slash (ft/path-join to (ft/basename fr)))]
           (copy cm fr to)
           (set-metadata cm metapath copy-key fr "")
           (set-owner cm to user)))

       {:sources from :dest to})))

(defn delete-trash
  [user]
  (with-jargon (jargon-config) [cm]
    (validators/user-exists cm user)

    (let [trash-dir  (user-trash-dir cm user)
          trash-list (mapv #(.getAbsolutePath %) (list-in-dir cm (ft/rm-last-slash trash-dir)))]
      (doseq [trash-path trash-list]
        (delete cm trash-path))
      {:trash trash-dir
       :paths trash-list})))

(defn add-tickets
  [user tickets public?]
  (with-jargon (jargon-config) [cm]
    (validators/user-exists cm user)

    (let [all-paths      (mapv :path tickets)
          all-ticket-ids (mapv :ticket-id tickets)]
      (validators/all-paths-exist cm all-paths)
      (validators/all-paths-writeable cm user all-paths)
      (validators/all-tickets-nonexistant cm user all-ticket-ids)

      (doseq [tm tickets]
        (create-ticket cm user (:path tm) (:ticket-id tm))
        (when public?
          (.addTicketGroupRestriction (ticket-admin-service cm user) (:ticket-id tm) "public")))

      {:user user :tickets (mapv #(ticket-map cm user %) all-ticket-ids)})))

(defn remove-tickets
  [user ticket-ids]
  (with-jargon (jargon-config) [cm]
    (validators/user-exists cm user)
    (validators/all-tickets-exist cm user ticket-ids)

    (let [all-paths (mapv #(.getIrodsAbsolutePath (ticket-by-id cm user %)) ticket-ids)]
      (validators/all-paths-writeable cm user all-paths)
      (doseq [ticket-id ticket-ids]
        (delete-ticket cm user ticket-id))
      {:user user :tickets ticket-ids})))

(defn list-tickets-for-paths
  [user paths]
  (with-jargon (jargon-config) [cm]
    (validators/user-exists cm user)
    (validators/all-paths-exist cm paths)
    (validators/all-paths-readable cm user paths)

    {:tickets
     (apply merge (mapv #(hash-map %1 (ticket-ids-for-path cm user %1)) paths))}))

(defn paths-contain-char
  [paths char]
  (when-not (good-string? char)
    (throw+ {:error_code ERR_BAD_OR_MISSING_FIELD
             :character char}))

  (apply merge (map #(hash-map %1 (not (nil? (re-seq (re-pattern char) %1)))) paths)))

(defn parent-dirs
  [user path]
  (let [pdirs (atom [])]
    (process-parent-dirs
     #(reset! pdirs (conj @pdirs %1))
     #(and (not (nil? %1))
           (not (= %1 (user-home-dir user)))) path)
    @pdirs))

(defn all-parent-dirs
  [user paths]
  (sort-by
   #(count (string/split %1 #"/")) >
   (vec (apply set/union (map #(set (parent-dirs user %1)) paths)))))

(defn looped-new-name
  "Iterates over the path, appending a _# to the end until a path that doesn't already exist is
   found."
  [cm path new-char]
  (loop [idx 0]
    (let [new-path (string/replace path #" " (str new-char "_" idx))]
      (if-not (exists? cm new-path)
        new-path
        (recur (inc idx))))))

(defn new-name
  "Creates a new name for the given path by replacing all spaces with the provided new-char.
   If the path is indicated to be a parent with the :parent flag, then the new name will NOT have
   a _# appended to it if the name already exists."
  [cm path new-char & {:keys [parent] :or {parent false}}]
  (let [new-path (string/replace path #" " new-char)]
    (if (or parent (not (exists? cm new-path)))
      new-path
      (looped-new-name cm path new-char))))

(defn has-space?
  "Returns a truthy value if the path contains a space somewhere in it."
  [path]
  (re-seq (re-pattern " ") path))

(defn move-spacey-path
  "Takes in a path and a new-char, replaces all spaces in the path with new-char, and returns a map
   with the original path as the key and the new path as the value. If the path is a parent, then
   the new name will not have a _# appended to it if it already exists."
  [cm user path new-char & {:keys [parent] :or {parent false}}]
  (when (has-space? (ft/basename path))
    (let [new-basename (new-name cm (ft/basename path) new-char :parent parent)
          new-path     (ft/path-join (ft/dirname path) new-basename)]
      (if (and (not (exists? cm new-path)) (exists? cm path))
        (move cm path new-path :user user :admin-users (irods-admins)))
      {path new-path})))

(defn fix-return-map
  [retmap new-char]
  (into {} (map #(hash-map (first %1) (string/replace (last %1) #" " new-char)) (seq retmap))))

(defn replace-spaces
  "Generates new paths by replacing all spaces with new-char."
  [user paths new-char]
  (with-jargon (jargon-config) [cm]
    (validators/user-exists cm user)
    (validators/all-paths-exist cm paths)
    (validators/user-owns-paths cm user paths)

    (when-not (good-string? new-char)
      (throw+ {:error_code ERR_BAD_OR_MISSING_FIELD
               :character new-char}))

    (let [parent-dirs (all-parent-dirs user paths)]
      (validators/user-owns-paths cm user parent-dirs)

      (let [mv-base         #(move-spacey-path cm user %1 new-char :parent false)
            mv-parent       #(move-spacey-path cm user %1 new-char :parent true)
            basename-merges (apply merge (map mv-base paths))
            parent-merges   (apply merge (map mv-parent parent-dirs))]
        {:paths (fix-return-map basename-merges new-char)}))))

(defn read-file-chunk
  "Reads a chunk of a file starting at 'position' and reading a chunk of length 'chunk-size'."
  [user path position chunk-size]
  (with-jargon (jargon-config) [cm]
    (validators/user-exists cm user)
    (validators/path-exists cm path)
    (validators/path-is-file cm path)
    (validators/path-readable cm user path)
    
    {:path       path
     :user       user
     :start      (str position)
     :chunk-size (str chunk-size)
     :file-size  (str (file-size cm path))
     :chunk      (read-at-position cm path position chunk-size)}))

(defn overwrite-file-chunk
  "Writes a chunk of a file starting at 'position' and extending to the length of the string."
  [user path position update-string]
  (with-jargon (jargon-config) [cm]
    (validators/user-exists cm user)
    (validators/path-exists cm path)
    (validators/path-is-file cm path)
    (validators/path-writeable cm user path)
    (overwrite-at-position cm path position update-string)
    {:path       path
     :user       user
     :start      (str position)
     :chunk-size (str (count (.getBytes update-string)))
     :file-size  (str (file-size cm path))}))
