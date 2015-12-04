(ns fhofherr.simple.core.config
  "Load and analyze a Clojure file containing Simple's configuration."
  (:require [clojure.tools.logging :as log]))

(def ^{:doc "Name of the namespace the file will be loaded into."}
  config-ns-name 'fhofherr.simple.projectdef)

(defn load-config
  "Load the Simple CI configuration file located under the given `path` into the
  [[config-ns-name]] namespace.While loading the file all public defs defined
  in the `dsl-ns` are available. Returns the namespace into which the
  configuration was loaded."
  [dsl-ns path]
  (when (find-ns config-ns-name)
    (log/info "Removing already existing configuration namespace:"
              config-ns-name)
    (remove-ns config-ns-name))
  (log/info "Loading configuration file" path)
  (binding [*ns* dsl-ns]
    (load-file path))
  (find-ns config-ns-name))

(defn find-ci-jobs
  "Find Simple CI jobs in the `cidef-ns` namespace. All mappings for whom 
  the `job?` predicate returns a truthy value  are treated as
  ci jobs. Returns the public private mappings of the found jobs."
  [cidef-ns job?]
  (as-> cidef-ns $
        (ns-publics $)
        (filter #(-> %
                     (second)
                     (var-get)
                     (job?))
                $)))
