(ns nibblonian.validators
  (:use clj-jargon.jargon
        clojure-commons.error-codes
        [slingshot.slingshot :only [try+ throw+]]))

(defn user-exists
  [user]
  (when-not (user-exists? user)
    (throw+ {:error_code ERR_NOT_A_USER
             :user user})))

(defn all-users-exist
  [users]
  (when-not (every? user-exists? users)
    (throw+ {:error_code ERR_NOT_A_USER
             :users (filterv
                     #(not (user-exists? %1))
                     users)})))

(defn path-exists
  [path]
  (when-not (exists? path)
    (throw+ {:error_code ERR_DOES_NOT_EXIST
             :path path})))

(defn all-paths-exist
  [paths]
  (when-not (every? exists? paths)
    (throw+ {:error_code ERR_DOES_NOT_EXIST
             :paths (filterv #(not (exists? %1)) paths)})))

(defn no-paths-exist
  [paths]
  (when (some exists? paths)
    (throw+ {:error_code ERR_EXISTS
             :paths (filterv #(exists? %) paths)})))

(defn path-readable
  [user path]
  (when-not (is-readable? user path)
    (throw+ {:error_code ERR_NOT_READABLE
             :path path
             :user user})))

(defn all-paths-readable
  [user paths]
  (when-not (every? #(is-readable? user %) paths)
    (throw+ {:error_code ERR_NOT_READABLE
             :path (filterv #(not (is-readable? user %)) paths)})))

(defn path-writeable
  [user path]
  (when-not (is-writeable? user path)
    (throw+ {:error_code ERR_NOT_WRITEABLE
             :path path})))

(defn all-paths-writeable
  [user paths]
  (when-not (paths-writeable? user paths)
    (throw+ {:paths (filterv #(not (is-writeable? user %)) paths)
             :error_code ERR_NOT_WRITEABLE})))

(defn path-not-exists
  [path]
  (when (exists? path)
    (throw+ {:path path
             :error_code ERR_EXISTS})))

(defn path-is-dir
  [path]
  (when-not (is-dir? path)
    (throw+ {:error_code ERR_NOT_A_FOLDER
             :path path})))

(defn path-is-file
  [path]
  (when-not (is-file? path)
    (throw+ {:error_code ERR_NOT_A_FILE
             :path path})))

(defn path-satisfies-predicate
  [path pred-func? pred-err]
  (when-not (pred-func? path)
    (throw+ {:paths path
             :error_code pred-err})))

(defn paths-satisfy-predicate
  [paths pred-func? pred-err]
  (when-not  (every? true? (mapv pred-func? paths))
    (throw+ {:error_code pred-err
             :paths (filterv #(not (pred-func? %)) paths)})))

(defn user-owns-paths
  [user paths]
  (when-not (every? (partial owns? user) paths)
    (throw+ {:error_code ERR_NOT_OWNER
             :user user
             :paths (filterv #(not (partial owns? user)) paths)})))