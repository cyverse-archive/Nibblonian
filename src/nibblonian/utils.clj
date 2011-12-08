(ns nibblonian.utils
  (:require [clojure.contrib.logging :as log])
  (:import [java.io File]))

(defn path-join
  "Joins two paths together and returns the resulting path as a string."
  [path1 path2]
  (log/debug (str "path-join " path1 " " path2))
  (. (java.io.File. (java.io.File. path1) path2) toString))

(defn rm-last-slash
  "Returns a new version of 'path' with the last slash removed.

   Parameters:
     path - String containing a path.

   Returns: New version of 'path' with the trailing slash removed."
  [path]
  (log/debug (str "rm-last-slash " path))
  (. path replaceAll "/$" ""))

(defn basename
  "Returns the basename of 'path'.

   This works by calling getName() on a java.io.File instance. It's prefered
   over last-dir-in-path for that reason.

   Parameters:
     path - String containing the path for an item in iRODS.

   Returns:
     String containing the basename of path."
  [path]
  (log/debug (str "basename " path))
  (. (File. path) getName))

(defn dirname
  "Returns the dirname of 'path'.

   This works by calling getParent() on a java.io.File instance.

   Parameters:
     path - String containing the path for an item in iRODS.

   Returns:
     String containing the dirname of path."
  [path]
  (log/debug (str "dirname " path))
  (. (File. path) getParent))

(defn add-trailing-slash
  "Adds a trailing slash to 'input-string' if it doesn't already have one."
  [input-string]
  (log/debug (str "add-trailing-slash " input-string))
  (if (not (. input-string endsWith "/"))
    (str input-string "/")
    input-string))

(defn dest-path
  "Functionally equivalent to a os.path.join in Python."
  [source dest]
  (log/debug (str "dest-path " source " " dest))
  (. (java.io.File. dest (. (java.io.File. source) getName)) toString))