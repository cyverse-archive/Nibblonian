(ns nibblonian.irods-actions
  (:require [clojure.tools.logging :as log]
            [clojure.java.io :as ds]
            [clojure.data.codec.base64 :as b64]
            [ring.util.codec :as cdc]
            [clojure.data.json :as json]
            [clojure-commons.file-utils :as ft]
            [clojure.string :as string]
            [nibblonian.validators :as validators]
            [nibblonian.riak :as riak])
  (:use [clj-jargon.jargon :exclude [init]]
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
(defn trash-base-dir [] (ft/path-join "/" (irods-zone) "trash" "home" (irods-user)))
(defn user-trash-dir [user] (ft/path-join (trash-base-dir) user))

(defn user-trash-dir?
  [user path-to-check]
  (= (ft/rm-last-slash path-to-check)
     (ft/rm-last-slash (user-trash-dir user))))

(defn id->label
  "Generates a label given a listing ID (read as absolute path)."
  [user id]
  (cond
   (user-trash-dir? user id)
   "Trash"

   (sharing? (ft/add-trailing-slash id))
   "Sharing"

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
     :label         (id->label user abspath)
     :permissions   (collection-perm-map cm user abspath)
     :hasSubDirs    true
     :date-created  (date-created-from-stat stat)
     :date-modified (date-mod-from-stat stat)
     :file-size     (size-from-stat stat))))

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

(defn gen-listing
  [cm user path filter-files include-files]
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
            :id            path
            :label         (id->label user path)
            :hasSubDirs    true
            :date-created  (created-date cm path)
            :date-modified (lastmod-date cm path)
            :permissions   (collection-perm-map cm user path)
            :folders       dirs)
        add-files)))

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
      (validators/path-exists cm root-path)

      (when (and set-own? (not (owns? cm user root-path)))
        (set-permissions cm user root-path false false true))

      (validators/path-readable cm user root-path)

      (dir-map-entry cm user (file cm root-path)))))

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
    (validators/user-exists cm user)
    (validators/path-writeable cm user (ft/dirname path))
    (validators/path-not-exists cm path)
    
    (mkdir cm path)
    (set-owner cm path user)
    {:path path :permissions (collection-perm-map cm user path)}))

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
      (validators/all-paths-writeable cm user sources)
      (validators/path-writeable cm user dest)
      (validators/no-paths-exist cm dest-paths)
      (move-all cm sources dest)
      {:sources sources :dest dest})))

(defn rename-path
  "High-level file renaming. Calls rename-func, passing it file-rename as the mv-func param."
  [user source dest]
  (with-jargon (jargon-config) [cm]
    (validators/user-exists cm user)
    (validators/path-exists cm source)
    (validators/path-writeable cm user source)
    (validators/path-not-exists cm dest)
    
    (let [result (move cm source dest)]
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
    (str
     (count
     (filterv
      #(not (contains? filter-users (.getUserName %1)))
      full-listing)))))

(defn merge-counts
  [stat-map cm path]
  (if (is-dir? cm path)
    (let [subs (group-by #(is-dir? cm %) (mapv #(.getAbsolutePath %) (list-in-dir cm path)))]
      (merge stat-map {:file-count (count (get subs false))
                       :dir-count  (count (get subs true))}))
    stat-map))

(defn path-stat
  [user path]
  (with-jargon (jargon-config) [cm]
    (validators/path-exists cm path)
    (let [retval (stat cm path)]
      (-> retval
          (merge {:permissions (permissions cm user path)
                  :share-count (count-shares cm user path)})
          (merge-counts cm path)))))

(defn- format-tree-urls
  [treeurl-maps]
  (if (pos? (count treeurl-maps))
    (json/read-json (:value (first (seq treeurl-maps))))
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
        json/read-json
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

(defn share
  [user share-withs fpaths perms]
  (with-jargon (jargon-config) [cm]
    (validators/user-exists cm user)
    (validators/all-users-exist cm share-withs)
    (validators/all-paths-exist cm fpaths)
    (validators/user-owns-paths cm user fpaths)
    
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
      (set-permissions cm user (ft/rm-last-slash root-dir) true false false))
    
    (assoc (sharing-data cm user root-dir inc-files filter-files) :label "Shared")))

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
    {:trash (user-trash-dir user)}))

(defn incremented-trash-path
  [cm user p]
  (let [inc-path #(ft/path-join (user-trash-dir user) (str (ft/basename p) "." %))]
    (loop [attempts 0]
      (if (exists? cm (inc-path attempts))
        (recur (inc attempts))
        (inc-path attempts)))))

(defn generated-trash-path
  [cm user p]
  (let [file-basename (ft/basename p)
        user-trash    (user-trash-dir user)]
    (if (exists? cm (ft/path-join user-trash file-basename))
      (incremented-trash-path cm user p)
      (ft/path-join user-trash file-basename))))

(defn move-to-trash
  [cm p user]
  (let [trash-path (generated-trash-path cm user p)]
    (move cm p trash-path)
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
      (validators/all-paths-writeable cm user paths)
      
      (when (some true? (mapv home-matcher paths))
        (throw+ {:error_code ERR_NOT_AUTHORIZED 
                 :paths (filterv home-matcher paths)}))
      
      (doseq [p paths]
        (if-not (.startsWith p (user-trash-dir user))
          (move-to-trash cm p user)
          (delete cm p)))
      
      {:paths paths})))

(defn trash-origin-path
  [cm user p]
  (if (attribute? cm p "ipc-trash-origin")
    (:value (first (get-attribute cm p "ipc-trash-origin")))
    (ft/path-join (user-home-dir user) (ft/basename p))))

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
    (loop [parent (ft/dirname path)]
      (when (and (not= parent (user-home-dir user)) (not (owns? cm user parent)))
        (log/warn (str "Restoring parent dir: " parent))
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
        (let [fully-restored (restoration-path cm user path)]
          (validators/path-not-exists cm fully-restored)
          (restore-parent-dirs cm user fully-restored)
          (validators/path-writeable cm user (ft/dirname fully-restored))
          (move cm path fully-restored)
          (reset! retval (assoc @retval path fully-restored))))
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
    
    (let [trash-dir  (user-trash-dir user)
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

