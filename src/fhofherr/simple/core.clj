(ns fhofherr.simple.core
  (:require [fhofherr.simple.core.dsl] ; Required to pass it to load-config
            [fhofherr.simple.core [config :as config]
             [job-fn :as job-fn]
             [job-descriptor :as jobs]
             [job-execution :as job-ex]
             [job-execution-context :as ex-ctx]]))

(defn load-core
  [project-dir config-file]
  (let [js (as-> (str project-dir "/" config-file) $
                 (config/load-config (the-ns 'fhofherr.simple.core.dsl) $)
                 (config/find-ci-jobs $ job-fn/job-fn?)
                 (map (fn [[s v]] [(name s) (jobs/make-job-descriptor v)]) $)
                 (into {} $))]
    {:jobs js
     :project-dir project-dir}))

(defn has-job?
  [core job-name]
  (contains? (:jobs core) (name job-name)))

(defn start-job!
  [core job-name]
  (if-let [jd (get-in core [:jobs (name job-name)])]
    (as-> core $
          (:project-dir $)
          (ex-ctx/make-job-execution-context $)
          (job-ex/make-job-execution $)
          (jobs/schedule-job! jd $))))
