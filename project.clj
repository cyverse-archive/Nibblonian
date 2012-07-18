(defproject nibblonian "0.0.5-SNAPSHOT"
  :description "RESTful interface into iRODS."
  :dependencies [[org.clojure/clojure "1.3.0"]
                 [org.clojure/java.jdbc "0.1.0"]
                 [org.clojure/data.json "0.1.1"]
                 [org.clojure/tools.logging "0.2.3"]
                 [org.clojure/java.classpath "0.1.0"]
                 [org.clojure/data.codec "0.1.0"]
                 [postgresql/postgresql "9.0-801.jdbc4"]
                 [org.iplantc/clojure-commons "1.1.0-SNAPSHOT"]
                 [org.iplantc/clj-jargon "0.1.0-SNAPSHOT"]
                 [compojure "1.0.1"]
                 [org.clojure/tools.cli "0.2.1"]
                 [ring/ring-jetty-adapter "1.0.1"]
                 [ring/ring-devel "1.0.1"]
                 [slingshot "0.10.1"]
                 [org.irods.jargon/jargon-core "3.0.1-SNAPSHOT"]
                 [org.irods.jargon/jargon-test "3.0.1-SNAPSHOT"]
                 [org.irods.jargon/jargon-data-utils "3.0.1-SNAPSHOT"]
                 [org.irods.jargon.transfer/jargon-transfer-engine "3.0.1-SNAPSHOT"]
                 [org.irods.jargon/jargon-security "3.0.1-SNAPSHOT"]
                 [log4j/log4j "1.2.16"]
                 [swank-clojure "1.3.1"]
		 [criterium "0.2.1"]]
  :aot [nibblonian.core]
  :main nibblonian.core
  :dev-dependencies [[lein-ring "0.5.4"]
                     [clj-http "0.3.2"]
                     [org.cloudhoist/stevedore "0.7.1"]
		     [criterium "0.2.1"]]
  :extra-classpath-dirs ["local-conf"]
  :ring {:init nibblonian.controllers/init
         :handler nibblonian.core/app}
  :repositories {"iplantCollaborative"
                 "http://projects.iplantcollaborative.org/archiva/repository/internal/"
                 
                 "renci.repository"
                 "http://ci-dev.renci.org/nexus/content/repositories/snapshots/"
                 
                 "sonatype"
                 "http://oss.sonatype.org/content/repositories/releases"})

