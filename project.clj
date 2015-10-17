(defproject simple "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "https://github.com/fhofherr/simple"
  :license {:name "MIT"
            :url "http://opensource.org/licenses/MIT"}
  :scm {:name "git"
        :url "https://github.com/fhofherr/simple"}
  :dependencies [[org.clojure/clojure "1.7.0"]]
  :main ^:skip-aot fhofherr.simple.main
  :target-path "target/%s"
  :test-selectors {:unit (complement :integration)
                   :integration :integration}
  :profiles {:dev {:source-paths ["dev"]
                   :resource-paths ["dev-resources"]
                   :dependencies [[org.clojure/tools.namespace "0.2.11"]]}
             :uberjar {:aot :all}})
