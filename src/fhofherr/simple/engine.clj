(ns fhofherr.simple.engine
  (:require [fhofherr.simple.dsl] ; Required to pass it to load-config
            [fhofherr.simple.engine [config :as config]
             [job-descriptor :as jobs]
             [job-execution :as job-ex]
             [job-execution-context :as ex-ctx]]))

(defn load-engine
  [project-dir config-file]
  (let [js (as-> (str project-dir "/" config-file) $
                 (config/load-config (the-ns 'fhofherr.simple.dsl) $)
                 (config/find-ci-jobs $)
                 (map (fn [[s v]] [(name s) (jobs/make-job-descriptor v)]) $)
                 (into {} $))]
    {:jobs js
     :project-dir project-dir}))

(defn has-job?
  [engine job-name]
  (contains? (:jobs engine) (name job-name)))

(defn start-job!
  [engine job-name]
  (if-let [jd (get-in engine [:jobs (name job-name)])]
    (as-> engine $
          (:project-dir $)
          (ex-ctx/make-job-execution-context $)
          (job-ex/make-job-execution $)
          (jobs/schedule-job! jd $))))
