(ns fhofherr.simple.engine.config
  (:require [fhofherr.simple.engine.jobs :as jobs]))

(def config-ns-name 'fhofherr.simple.projectdef)

(defn load-config
  "Load the Simple CI configuration file located under the given `path`.
  Returns the namespace into which the configration was loaded."
  [path]
  (when (find-ns config-ns-name)
    (remove-ns config-ns-name))
  ;; TODO don't hard code dsl namespace
  (binding [*ns* (find-ns 'fhofherr.simple.dsl)]
    (load-file path))
  (find-ns config-ns-name))

(defn find-ci-jobs
  "Find Simple CI jobs in the `cidef-ns` namespace. All mappings with
  a truthy value for the key `:ci-job?` in their meta data are treated as
  Simple CI jobs. Returns the public private mappings of the found jobs."
  [cidef-ns]
  (as-> cidef-ns $
       (ns-publics $)
       ;; TODO use simple-ci-job? here
       (filter #(-> %
                    (second)
                    (var-get)
                    (jobs/simple-ci-job?))
               $)))
