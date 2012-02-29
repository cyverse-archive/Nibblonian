(ns nibblonian.test.integration
  (:use [clojure.test]
        [pallet.stevedore]
        [pallet.stevedore.bash])
  (:require [clj-http.client :as cl]
            [clojure.data.json :as json]
            [clojure.java.io :as io]
            [pallet.common.shell :as shell]))

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

(defmacro postpathjson [url body-map user path]
  `(cl/post ~url
      {:content-type :json
       :query-params {"user" ~user
                      "path" ~path}
       :body (json/json-str ~body-map)
       :throw-exceptions false}))

(defmacro postjson [url body-map user]
  `(cl/post ~url
      {:content-type :json
       :query-params {"user" ~user}
       :body (json/json-str ~body-map)
       :throw-exceptions false}))

(defmacro getjson [url user path]
  `(cl/get ~url
      {:throw-exceptions false
       :query-params {"user" ~user
                      "path" (user-path ~path)}}))

(defn create-dir [dirname]
  (let [reqj {:path (user-path dirname)}]
    (postjson create reqj nuser)))

(defn delete-dir [dirname]
  (let [reqj {:paths [(user-path dirname)]}]
    (postjson delete-dirs reqj nuser)))

(defn add-avu [fpath attr value unit]
  (let [req {:attr attr :value value :unit unit}]
    (postpathjson file-metadata req nuser (user-path fpath))))

(defn put-file [remote-dir local-filepath]
  (shell/bash
    (with-script-language :pallet.stevedore.bash/bash
      (script 
        (icd ~(user-path remote-dir))
        (iput ~local-filepath)))))

(defn local-file [fname contents]
  (spit fname contents)
  fname)

(deftest test-create
  (let [resp (create-dir "test-create0")
        body (json/read-json (:body resp))]
    (is (= (:status resp) 200))
    (is (= (:status body) "success"))
    (is (= (:action body) "create"))
    (is (= (:path body) (user-path "test-create0")))
    (is (= (get-in body [:permissions :read]) true))
    (is (= (get-in body [:permissions :write]) true)))
  (delete-dir "test-create0"))

(deftest test-move-files
  (create-dir "test-move-files0")
  (create-dir "test-move-files1")
  (spit "test-1.txt" "test-1")
  (spit "test-2.txt" "test-2")
  (put-file "test-move-files0" "test-1.txt")
  (put-file "test-move-files0" "test-2.txt")
  
  (let [reqj {:sources [(user-path "test-move-files0/test-1.txt")
                        (user-path "test-move-files0/test-2.txt")]
              :dest (user-path "test-move-files1")}
        resp (postjson move-files reqj nuser)
        body (json/read-json (:body resp))]
    (is (= (:status resp) 200))
    (is (= (:status body) "success"))
    (is (= (:action body) "move-files"))
    (is (= (:sources body) (:sources reqj)))
    (is (= (:dest body) (:dest reqj))))
  
  (delete-dir "test-move-files0")
  (delete-dir "test-move-files1")
  (io/delete-file "test-1.txt")
  (io/delete-file "test-2.txt"))

(deftest test-move-dirs
  (create-dir "test-move-dir0")
  (create-dir "test-move-dir1")
  (create-dir "test-move-dir2")
  
  (let [reqj {:sources [(user-path "test-move-dir1")
                        (user-path "test-move-dir2")]
              :dest (user-path "test-move-dir0")}
        resp (postjson move-dirs reqj nuser)
        body (json/read-json (:body resp))]
    (is (= (:status resp) 200))
    (is (= (:status body) "success"))
    (is (= (:action body) "move-dirs"))
    (is (= (:sources body) (:sources reqj)))
    (is (= (:dest body) (:dest reqj))))
  
  (delete-dir "test-move-dir0/test-move-dir1")
  (delete-dir "test-move-dir0/test-move-dir2"))

(deftest test-rename-dir
  (create-dir "test-move-dir0")
  (let [reqj {:source (user-path "test-move-dir0")
              :dest (user-path "test-move-dir1")}
        resp (postjson rename-dir reqj nuser)
        body (json/read-json (:body resp))]
    (is (= (:status resp) 200))
    (is (= (:status body) "success"))
    (is (= (:action body) "rename-directory"))
    (is (= (:source body) (:source reqj)))
    (is (= (:dest body) (:dest reqj))))
  (delete-dir "test-move-dir1"))

(deftest test-rename-file
  (spit "test-rename0.txt" "test-rename")
  (create-dir "test-rename0")
  (put-file "test-rename0" "test-rename0.txt")
  (let [reqj {:source (user-path "test-rename0/test-rename0.txt")
              :dest (user-path "test-rename0/test-rename1.txt")}
        resp (postjson rename-file reqj nuser)
        body (json/read-json (:body resp))]
    (is (= (:status resp) 200))
    (is (= (:status body) "success"))
    (is (= (:action body) "rename-file"))
    (is (= (:source body) (:source reqj)))
    (is (= (:dest body) (:dest reqj))))
  (delete-dir "test-rename0")
  (io/delete-file "test-rename0.txt"))

(deftest test-delete-dirs
  (create-dir "test-create0")
  
  (let [reqj {:paths [(user-path "test-create0")]}
        resp (postjson delete-dirs reqj nuser)
        body (json/read-json (:body resp))]
    (is (= (:status resp) 200))
    (is (= (:status body) "success"))
    (is (= (:action body) "delete-dirs"))
    (is (= (:paths body) [(user-path "test-create0")]))))

(deftest test-delete-files
  (create-dir "test-delete-files0")
  (spit "test-delete0.txt" "test-delete0")
  (spit "test-delete1.txt" "test-delete1")
  (put-file "test-delete-files0" "test-delete0.txt")
  (put-file "test-delete-files0" "test-delete1.txt")
  (let [reqj {:paths [(user-path "test-delete-files0/test-delete0.txt")
                      (user-path "test-delete-files0/test-delete1.txt")]}
        resp (postjson delete-files reqj nuser)
        body (json/read-json (:body resp))]
    (is (= (:status resp) 200))
    (is (= (:status body) "success"))
    (is (= (:action body) "delete-files"))
    (is (= (:paths body) (:paths reqj))))
  (delete-dir "test-delete-files0")
  (io/delete-file "test-delete0.txt")
  (io/delete-file "test-delete1.txt"))

(deftest test-preview
  (create-dir "test-preview")
  (spit "test-preview.txt" "testing preview")
  (put-file "test-preview" "test-preview.txt")
  (let [resp (getjson preview nuser "test-preview/test-preview.txt")
        body (json/read-json (:body resp))]
    (is (= (:status resp) 200))
    (is (= (:status body) "success"))
    (is (= (:action "preview")))
    (is (= (:preview body) "testing preview")))
  (delete-dir "test-preview")
  (io/delete-file "test-preview.txt"))

(deftest test-manifest
  (create-dir "test-manifest")
  (spit "test-manifest.txt" "testing manifest")
  (put-file "test-manifest" "test-manifest.txt")
  (let [resp (getjson manifest nuser "test-manifest/test-manifest.txt")
        body (json/read-json (:body resp))]
    (is (= (:status resp) 200))
    (is (= (:status body) "success"))
    (is (= (:action body) "manifest"))
    (is (= (:tree-urls body) []))
    (is (= (:preview body) (str "file/preview?user=" nuser "&path=%2Fiplant%2Fhome%2F" nuser "%2Ftest-manifest%2Ftest-manifest.txt"))))
  (delete-dir "test-manifest")
  (io/delete-file "test-manifest.txt"))

(deftest test-exists
  (create-dir "test-exists")
  (create-dir "test-exists1")
  (let [reqj {:paths [(user-path "test-exists")
                      (user-path "test-exists1")]}
        resp (postjson exists reqj nuser)
        body (json/read-json (:body resp) false)]
    (is (= (:status resp) 200))
    (is (= (get body "status") "success"))
    (is (= (get body "action") "exists"))
    (is (= (get body "paths") {(str "/iplant/home/" nuser "/test-exists") true
                               (str "/iplant/home/" nuser "/test-exists1") true})))
  (delete-dir "test-exists")
  (delete-dir "test-exists1"))

(deftest test-file-metadata
  (create-dir "test-file-metadata")
  (spit "test-file-metadata.txt" "test-file-metadata")
  (put-file "test-file-metadata" "test-file-metadata.txt")
  (let [reqj {:attr "test-attr"
              :value "test-value"
              :unit "test-unit"}
        resp (postpathjson file-metadata reqj nuser (user-path "test-file-metadata/test-file-metadata.txt"))
        body (json/read-json (:body resp))]
    (is (= (:status resp) 200))
    (is (= (:status body) "success"))
    (is (= (:action body) "set-metadata"))
    (is (= (:user body) nuser))
    (is (= (:path body) (user-path "test-file-metadata/test-file-metadata.txt"))))
  (delete-dir "test-file-metadata")
  (io/delete-file "test-file-metadata.txt"))

(deftest test-file-metadata-batch
  (create-dir "test-file-metadata-batch")
  (spit "lol.txt" "test-file-metadata-batch")
  (put-file "test-file-metadata-batch" "lol.txt")
  (add-avu (user-path "test-file-metadata-batch/lol.txt") "test-attr0" "test-val0" "test-unit0")
  (add-avu (user-path "test-file-metadata-batch/lol.txt") "test-attr00" "test-val00" "test-unit00")
  (let [reqj {:add [{:attr "test-attr1"
                     :value "test-value1"
                     :unit "test-unit1"}
                    {:attr "test-attr2"
                     :value "test-value2"
                     :unit "test-unit2"}]
              :delete ["test-attr0" "test-attr00"]}
        resp (postpathjson file-metadata-batch reqj nuser (user-path "test-file-metadata-batch/lol.txt"))
        body (json/read-json (:body resp))]
    (is (= (:status resp) 200))
    (is (= (:status body) "success"))
    (is (= (:action body) "set-metadata-batch"))
    (is (= (:user body) nuser))
    (is (= (:path body) (user-path "test-file-metadata-batch/lol.txt"))))
  (delete-dir "test-file-metadata-batch")
  (io/delete-file "lol.txt"))
