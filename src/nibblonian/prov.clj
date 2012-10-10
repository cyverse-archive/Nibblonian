(ns nibblonian.prov
  (:require [clojure-commons.provenance :as p]
            [clojure-commons.file-utils :as f]
            [nibblonian.config :as cfg]
            [clj-jargon.jargon :as jg])
  (:import [org.irods.jargon.datautils.shoppingcart FileShoppingCart]
           [org.irods.jargon.core.pub.domain IRODSDomainObject]))

;;;Event Names
(def root "root")
(def home "home")
(def file-exists "file-exists")
(def dir-exists "directory-exists")
(def stat-file "stat-file")
(def stat-dir "stat-directory")
(def download "download")
(def download-cart "download-cart")
(def upload-cart "upload-cart")
(def create-dir "create-directory")
(def list-dir "list-directory")
(def rename-dir "rename-directory")
(def delete-dir "delete-directory")
(def move-dir "move-directory")
(def preview-file "preview-file")
(def file-manifest "file-manifest")
(def get-file-metadata "get-file-metadata")
(def set-file-metadata "set-file-metadata")
(def del-file-metadata "delete-file-metadata")
(def get-tree-urls "get-tree-urls")
(def set-file-metadata-batch "set-file-metadata-batch")
(def get-dir-metadata "get-directory-metadata")
(def set-dir-metadata "set-directory-metadata")
(def del-dir-metadata "del-dir-metadata")
(def set-dir-metadata-batch "set-directory-metadata-batch")
(def share-file "share-file")
(def share-dir "share-directory")
(def unshare-file "unshare-file")
(def unshare-dir "unshare-directory")
(def quota "quota")
(def get-user-file-perms "get-user-file-permissions")
(def get-user-dir-perms "get-user-directory-permissions")
(def restore-file "restore-file")
(def restore-dir "restore-dir")
(def copy-file "copy-file")
(def copy-dir "copy-directory")

;;;Category Names
(def irods-file "irods-file")
(def irods-dir "irods-directory")
(def irods-avu "irods-avu")
(def irods-cart "irods-cart")
(def irods-listing "irods-listing")

;;;Utility functions
(defn determine-category
  [cm obj]
  (cond
   (jg/is-dir? cm obj)              irods-dir
   (jg/is-file? cm obj)             irods-file
   (instance? FileShoppingCart obj) irods-cart
   :else                            nil))

(defn avu?
  [obj]
  (and (map? obj)
       (contains? obj :attr)
       (contains? obj :value)
       (contains? obj :unit)))

(defn irods-domain-obj
  [cm obj]
  (cond
   (and (string? obj)
        (jg/is-dir? cm obj))
   (jg/collection cm obj)

   (and (string? obj)
        (jg/is-file? cm obj))
   (jg/dataobject cm obj)

   (instance? FileShoppingCart obj)
   obj
   
   :else obj))

(defn object-id
  [cm user obj]
  (let [domain-obj (irods-domain-obj cm obj)]
    (cond
     (instance? domain-obj IRODSDomainObject)
     (str (cfg/irods-zone)
          "||"
          (.. domain-obj getCreatedAt getTime)
          "||"
          (.. domain-obj getAbsolutePath))

     (instance? domain-obj FileShoppingCart)
     (str (cfg/irods-zone)
          "||"
          user
          "||"
          (str (.getTime (java.util.Date.))))

     :else
     "This is a string")))

(defn object-name
  [cm user obj]
  (let [domain-obj (irods-domain-obj cm obj)]
    (cond
     (instance? domain-obj IRODSDomainObject)
     (f/basename (.getAbsolutePath domain-obj))

     (instance? domain-obj FileShoppingCart)
     (str "shopping-cart-" user)

     (avu? domain-obj)
     (str (:attr domain-obj)
          "-"
          (:value domain-obj)
          "-"
          (:unit domain-obj))

     :else
     "This is another string.")))

(defn arg-map
  [cm user obj event &
   {:keys [proxy-user category data]
    :or {proxy-user (cfg/irods-user)
         data       nil
         category   (determine-category cm obj)}}]
  (let [obj-id (object-id cm obj)
        svc    (cfg/service-name)
        purl   (cfg/prov-url)]
    (p/prov-map purl obj-id user svc event category proxy-user data)))

(defn register
  [cm user obj desc]
  (let [obj-id (object-id cm user obj)
        obj-nm (object-name cm user obj)]
    (if-not (p/exists? (cfg/prov-url) obj-id)
      (p/register (cfg/prov-url) obj-id obj-nm desc))))