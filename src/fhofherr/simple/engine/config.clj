(ns fhofherr.simple.engine.config
  (:require [clojure.tools.logging :as log]
            [fhofherr.simple.engine.jobs :as jobs]))

(def config-ns-name 'fhofherr.simple.projectdef)

(defn load-config
  "Load the Simple CI configuration file located under the given `path`.
  Returns the namespace into which the configration was loaded."
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
  "Find Simple CI jobs in the `cidef-ns` namespace. All mappings with
  a truthy value for the key `:ci-job?` in their meta data are treated as
  Simple CI jobs. Returns the public private mappings of the found jobs."
  [cidef-ns]
  (as-> cidef-ns $
       (ns-publics $)
       ;; TODO pass simple-ci-job? as param ==> no dependency to jobs
       (filter #(-> %
                    (second)
                    (var-get)
                    (jobs/simple-ci-job?))
               $)))
