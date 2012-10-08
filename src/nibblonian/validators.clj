(ns nibblonian.validators
  (:use clj-jargon.jargon
        clojure-commons.error-codes
        [slingshot.slingshot :only [try+ throw+]]))

(defn user-exists
  [cm user]
  (when-not (user-exists? cm user)
    (throw+ {:error_code ERR_NOT_A_USER
             :user user})))

(defn all-users-exist
  [cm users]
  (when-not (every? #(user-exists? cm %) users)
    (throw+ {:error_code ERR_NOT_A_USER
             :users (filterv
                     #(not (user-exists? %1))
                     users)})))

(defn path-exists
  [cm path]
  (when-not (exists? cm path)
    (throw+ {:error_code ERR_DOES_NOT_EXIST
             :path path})))

(defn all-paths-exist
  [cm paths]
  (when-not (every? #(exists? cm %) paths)
    (throw+ {:error_code ERR_DOES_NOT_EXIST
             :paths (filterv #(not (exists? cm  %1)) paths)})))

(defn no-paths-exist
  [cm paths]
  (when (some #(exists? cm %) paths)
    (throw+ {:error_code ERR_EXISTS
             :paths (filterv #(exists? cm %) paths)})))

(defn path-readable
  [cm user path]
  (when-not (is-readable? cm user path)
    (throw+ {:error_code ERR_NOT_READABLE
             :path path
             :user user})))

(defn all-paths-readable
  [cm user paths]
  (when-not (every? #(is-readable? cm user %) paths)
    (throw+ {:error_code ERR_NOT_READABLE
             :path (filterv #(not (is-readable? cm user %)) paths)})))

(defn path-writeable
  [cm user path]
  (when-not (is-writeable? cm user path)
    (throw+ {:error_code ERR_NOT_WRITEABLE
             :path path})))

(defn all-paths-writeable
  [cm user paths]
  (when-not (paths-writeable? cm user paths)
    (throw+ {:paths (filterv #(not (is-writeable? cm user %)) paths)
             :error_code ERR_NOT_WRITEABLE})))

(defn path-not-exists
  [cm path]
  (when (exists? cm path)
    (throw+ {:path path
             :error_code ERR_EXISTS})))

(defn path-is-dir
  [cm path]
  (when-not (is-dir? cm path)
    (throw+ {:error_code ERR_NOT_A_FOLDER
             :path path})))

(defn path-is-file
  [cm path]
  (when-not (is-file? cm path)
    (throw+ {:error_code ERR_NOT_A_FILE
             :path path})))

(defn path-satisfies-predicate
  [cm path pred-func? pred-err]
  (when-not (pred-func? cm  path)
    (throw+ {:paths path
             :error_code pred-err})))

(defn paths-satisfy-predicate
  [cm paths pred-func? pred-err]
  (when-not  (every? true? (mapv #(pred-func? cm %) paths))
    (throw+ {:error_code pred-err
             :paths (filterv #(not (pred-func? cm %)) paths)})))

(defn user-owns-paths
  [cm user paths]
  (when-not (every? (partial #(owns? cm %) user) paths)
    (throw+ {:error_code ERR_NOT_OWNER
             :user user
             :paths (filterv #(not (partial #(owns? cm %) user)) paths)})))
