(defproject simple "0.1.0-SNAPSHOT"
  :description "A very simple CI server for individual projects."
  :url "https://github.com/fhofherr/simple"
  :license {:name "MIT"
            :url "http://opensource.org/licenses/MIT"}
  :scm {:name "git"
        :url "https://github.com/fhofherr/simple"}
  :dependencies [[org.clojure/clojure "1.7.0"]]
  :main ^:skip-aot fhofherr.simple.main
  :global-vars {*warn-on-reflection* true}
  :target-path "target/%s"
  :test-selectors {:unit (complement :integration)
                   :integration :integration}
  :plugins [[codox "0.8.15" :exclusions [[org.clojure/clojure]]]]
  :codox {:output-dir "target/doc/api"
          :src-dir-uri "https://github.com/fhofherr/simple/blob/master/"
          :src-linenum-anchor-prefix "L"
          :defaults {:doc/format :markdown}
          :exclude [user]}
  :profiles {:dev {:source-paths ["dev"]
                   :resource-paths ["dev-resources" "test-resources"]
                   :dependencies [[org.clojure/tools.namespace "0.2.11"]]}
             :test {:resource-paths ["test-resources"]}
             :uberjar {:aot :all}})
