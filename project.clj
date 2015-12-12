(defproject simple "0.2.0-SNAPSHOT"
  :description "A very simple CI server for individual projects."
  :url "https://github.com/fhofherr/simple"
  :license {:name "MIT"
            :url "http://opensource.org/licenses/MIT"}
  :scm {:name "git"
        :url "https://github.com/fhofherr/simple"}
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [org.clojure/tools.logging "0.3.1"]]
  :main ^:skip-aot fhofherr.simple.main
  :global-vars {*warn-on-reflection* true}
  :target-path "target/%s"
  :test-selectors {:unit (complement :integration)
                   :integration :integration}
  :plugins [[lein-codox "0.9.0" :exclusions [[org.clojure/clojure]]]
            [lein-cljfmt "0.3.0"]]
  :codox {:output-path "target/doc"
          :source-uri "https://github.com/fhofherr/simple/blob/master/{filepath}#L{line}"
          :metadata {:doc/format :markdown}
          :doc-paths ["doc"
                      "README.md"
                      "CHANGELOG.md"]
          :namespaces [#"fhofherr\.simple.*"
                       #"fhofherr\.clj-io.*"]}
  :profiles {:dev {:source-paths ["dev"]
                   :resource-paths ["dev-resources" "test-resources"]
                   :dependencies [[org.clojure/tools.namespace "0.2.11"]]}
             :test {:resource-paths ["test-resources"]}
             :uberjar {:aot :all}})
