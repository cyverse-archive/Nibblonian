(defproject nibblonian/nibblonian "0.2.0-SNAPSHOT"
  :dependencies [[org.clojure/clojure "1.4.0"]
                 [org.clojure/java.jdbc "0.1.0"]
                 [org.clojure/tools.logging "0.2.3"]
                 [org.clojure/java.classpath "0.1.0"]
                 [org.clojure/data.codec "0.1.0"]
                 [org.iplantc/clojure-commons "1.4.0-SNAPSHOT"]
                 [org.iplantc/clj-jargon "0.2.2-SNAPSHOT"]
                 [cheshire "5.0.1"]
                 [clj-http "0.3.2"]
                 [com.cemerick/url "0.0.7"]
                 [compojure "1.0.1"]
                 [org.clojure/tools.cli "0.2.1"]
                 [ring/ring-jetty-adapter "1.0.1"]
                 [ring/ring-devel "1.0.1"]
                 [slingshot "0.10.1"]
                 [log4j/log4j "1.2.16"]
                 [swank-clojure "1.3.1"]
                 [org.apache.tika/tika-core "1.1"]]
  :ring {:init nibblonian.controllers/init,
         :handler nibblonian.core/app}
  :profiles {:dev
             {:resource-paths ["local-conf"],
              :dependencies [[clj-http "0.3.2"]
                             [org.cloudhoist/stevedore "0.7.1"]
                             [criterium "0.2.1"]]}}
  :iplant-rpm {:summary "nibblonian"
               :dependencies ["iplant-service-config >= 0.1.0-5"]
               :config-files ["log4j.properties"]
               :config-path "conf"}
  :repositories {"iplantCollaborative"
                 "http://projects.iplantcollaborative.org/archiva/repository/internal/",
                 "renci.repository"
                 "http://ci-dev.renci.org/nexus/content/repositories/snapshots/",
                 "sonatype"
                 "http://oss.sonatype.org/content/repositories/releases"}
  :aot [nibblonian.core]
  :main nibblonian.core
  :min-lein-version "2.0.0"
  :plugins [[lein-ring "0.5.4"]
            [org.iplantc/lein-iplant-rpm "1.4.0-SNAPSHOT"]]
  :description "RESTful interface into iRODS.")
