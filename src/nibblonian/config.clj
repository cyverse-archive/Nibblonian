(ns nibblonian.config
  (:require [clojure.string :as string]
            [clojure.tools.logging :as log]
            [clojure-commons.clavin-client :as cl]
            [clojure-commons.props :as prps]
            [clj-jargon.jargon :as jargon]))

(def props (atom nil))

(defn max-retries []
  (java.lang.Integer/parseInt
    (get @props "nibblonian.app.max-retries")))

(defn retry-sleep []
  (java.lang.Integer/parseInt
    (get @props "nibblonian.app.retry-sleep")))

(defn preview-size []
  (java.lang.Integer/parseInt
    (get @props "nibblonian.app.preview-size")))

(defn data-threshold []
  (java.lang.Integer/parseInt
    (get @props "nibblonian.app.data-threshold")))

(defn community-data []
  (get @props "nibblonian.app.community-data"))

(defn filter-files []
  (string/split (get @props "nibblonian.app.filter-files") #","))

(defn perms-filter []
  (string/split (get @props "nibblonian.app.perms-filter") #","))

(defn use-trash []
  (java.lang.Boolean/parseBoolean
    (get @props "nibblonian.app.use-trash")))

(defn listen-port []
  (Integer/parseInt (get @props "nibblonian.app.listen-port")))

(defn copy-key []
  (or (get @props "nibblonian.app.copy-key")
      "ipc-de-copy-from"))

(defn irods-host [] (get @props "nibblonian.irods.host"))
(defn irods-port [] (get @props "nibblonian.irods.port"))
(defn irods-user [] (get @props "nibblonian.irods.user"))
(defn irods-pass [] (get @props "nibblonian.irods.password"))
(defn irods-home [] (get @props "nibblonian.irods.home"))
(defn irods-zone [] (get @props "nibblonian.irods.zone"))
(defn irods-resc [] (get @props "nibblonian.irods.defaultResource"))

(defn prov-url [] (get @props "nibblonian.prov-proxy-url"))
(defn service-name [] (or (get @props "nibblonian.service-name")))
(defn riak-base-url [] (get @props "nibblonian.riak.base-url"))
(defn riak-trees-bucket [] (get @props "nibblonian.riak.trees-bucket"))

(defn prov-enabled? []
  (java.lang.Boolean/parseBoolean
   (get @props "nibblonian.enable-provenance")))

(def jg-cfg (atom nil))

(defn jargon-config [] @jg-cfg)

(defn jargon-init
  []
  (jargon/init
   (irods-host)
   (irods-port)
   (irods-user)
   (irods-pass)
   (irods-home)
   (irods-zone)
   (irods-resc)
   :max-retries (max-retries)
   :retry-sleep (retry-sleep)
   :use-trash   (use-trash)))

(defn init []
  (let [tmp-props (prps/parse-properties "zkhosts.properties")
        zkurl     (get tmp-props "zookeeper")]
    (cl/with-zk
      zkurl
      (when-not (cl/can-run?)
        (log/warn
         "THIS APPLICATION CANNOT RUN ON THIS MACHINE. SO SAYETH ZOOKEEPER.")
        (log/warn "THIS APPLICATION WILL NOT EXECUTE CORRECTLY."))
      (reset! props (cl/properties "nibblonian")))) 
  (reset! jg-cfg (jargon-init)))

(defn local-init
  [local-config-path]
  (let [main-props (prps/read-properties local-config-path)]
    (reset! props main-props)
    (reset! jg-cfg (jargon-init))))