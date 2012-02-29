(ns nibblonian.test.integration
  (:use [clojure.test])
  (:require [clj-http.client :as cl]
            [clojure.data.json :as json]))

(def nurl "http://services-2.iplantcollaborative.org:31360")
(def nuser "wregglej")

(defn endpoint [url-path]
  (str nurl "/" url-path))

(def user-home (str "/iplant/home/" nuser))

(defn user-path [user-path]
  (str user-home "/" user-path))

(def create (endpoint "directory/create"))
(def upload (endpoint "upload"))
(def download (endpoint "download"))
(def list-dir (endpoint "directory"))
(def move-dirs (endpoint "directory/move"))
(def move-files (endpoint "file/move"))
(def rename-dir (endpoint "directory/rename"))
(def rename-file (endpoint "file/rename"))
(def delete-dirs (endpoint "directory/delete"))
(def delete-files (endpoint "file/delete"))
(def preview (endpoint "file/preview"))
(def manifest (endpoint "file/manifest"))
(def exists (endpoint "exists"))
(def file-metadata (endpoint "file/metadata"))
(def file-metadata-batch (endpoint "file/metadata-batch"))
(def tree-urls (endpoint "file/tree-urls"))

(defmacro postjson [url body-map user]
  `(cl/post ~url
      {:content-type :json
       :query-params {"user" ~user}
       :body (json/json-str ~body-map)
       :throw-exceptions false}))

(deftest test-create
  (let [reqj {:path (user-path "test-create0")}
        resp (postjson create reqj nuser)
        body (json/read-json (:body resp))]
    (is (= (:status resp) 200))
    (is (= (:status body) "success"))
    (is (= (:action body) "create"))
    (is (= (:path body) (user-path "test-create0")))
    (is (= (get-in body [:permissions :read]) true))
    (is (= (get-in body [:permissions :write]) true))))

(deftest test-delete
  (let [reqj {:paths [(user-path "test-create0")]}
        resp (postjson delete-dirs reqj nuser)
        body (json/read-json (:body resp))]
    (is (= (:status resp) 200))
    (is (= (:status body) "success"))
    (is (= (:action body) "delete-dirs"))
    (is (= (:paths body) [(user-path "test-create0")]))))