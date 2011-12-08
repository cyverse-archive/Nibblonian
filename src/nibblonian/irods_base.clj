(ns nibblonian.irods-base
  (:require [clojure.tools.logging :as log])
  (:import [org.irods.jargon.core.connection IRODSAccount]
           [org.irods.jargon.core.pub IRODSFileSystem]))

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

(defn init-irods
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

(defprotocol JARGON
  (user-groups           [j user])
  (user-dataobject-perms [j user data-path])
  (user-collection-perms [j user coll-path])
  (dataobject-perm-map   [j user data-path])
  (collection-perm-map   [j user coll-path])
  (dataobject-perm?      [j user data-path checked-perm])
  (dataobject-readable?  [j user data-path])
  (dataobject-writeable? [j user data-path])
  (owns-dataobject?      [j user data-path])
  (collection-perm?      [j user coll-path checked-perm])
  (collection-readable?  [j user coll-path])
  (collection-writeable? [j user coll-path])
  (owns-collection?      [j user coll-path])
  (file                  [j path])
  (exists?               [j path])
  (paths-exist?          [j paths])
  (is-file?              [j path])
  (is-dir?               [j path])
  (data-object           [j path])
  (collection            [j path])
  (lastmod-date          [j path])
  (created-date          [j path])
  (file-size             [j path])
  (response-map          [j action paths])
  (user-exists?          [j user])
  (set-owner             [j path owner])
  (set-inherits          [j path])
  (is-writeable?         [j user path])
  (is-readable?          [j user path])
  (last-dir-in-path      [j path])
  (sub-collections       [j path])
  (sub-collection-paths  [j path])
  (sub-dir-maps          [j user path])
  (sub-file-maps         [j user list-obj])
  (paths-writeable?      [j user paths]))

(defprotocol METADATA
  (map2avu                    [m avu-map])
  (decode-metadata-value      [m value])
  (get-metadata               [m dir-path])
  (get-attribute              [m dir-path attr])
  (attribute?                 [m dir-path attr])
  (set-metadata               [m dir-path attr value unit])
  (transform-map-for-deletion [m path avu-map])
  (delete-metadata            [m dir-path attr]))
