(ns fhofherr.simple.core
  "Simple CI's application core."
  (:require [fhofherr.simple.core.dsl] ; Required to pass it to load-config
            [fhofherr.simple.core [config :as config]
             [job-fn :as job-fn]
             [job-descriptor :as jobs-desc]
             [job-execution :as job-ex]
             [job-execution-context :as ex-ctx]
             [timer-service :as timer-service]
             [triggers :as triggers]
             [subsystems :as subsystems]]))

(declare start-job!)

(defn- start-timer-service
  [core]
  (timer-service/start-timer-service (:timer-service core))
  core)

(defn- stop-timer-service
  [core]
  (timer-service/stop-timer-service (:timer-service core))
  core)

(defn- configure-jobs-subsystem
  [cidef-ns]
  (as-> cidef-ns $
        (config/filter-publics $ jobs-desc/job-descriptor?)
        (map (fn [[s v]] [(name s) (var-get v)]) $)
        (into {} $)))

(defn- start-jobs-subsystem
  [core]
  (doseq [[_ job] (:jobs core)
          trigger-cfg (:triggers job)]
    (triggers/register-trigger core
                               trigger-cfg
                               #(start-job! core (:job-name job))))
  core)

(defn load-core
  "Bootstrap Simple CI using the `config-file` contained in `project-dir`."
  [project-dir config-file]
  (let [cidef-ns (as-> (str project-dir "/" config-file) $
                       (config/load-config (the-ns 'fhofherr.simple.core.dsl) $))
        jobs (configure-jobs-subsystem cidef-ns)]
    (-> {:project-dir project-dir}
        (subsystems/register-subsystem :timer-service
                                       (timer-service/make-timer-service)
                                       :start start-timer-service
                                       :stop stop-timer-service)
        (subsystems/register-subsystem :jobs jobs
                                       :start start-jobs-subsystem))))

(defn start-core
  [core]
  (-> core
      (subsystems/start)))

(defn stop-core
  [core]
  (-> core
      (subsystems/stop)))

(defn has-job?
  "Check if the `core` has a job named `job-name`."
  [core job-name]
  (contains? (:jobs core) (name job-name)))

(defn start-job!
  "Start the job named `job-name`."
  [core job-name]
  {:pre [(has-job? core job-name)]}
  (if-let [jd (get-in core [:jobs (name job-name)])]
    (as-> core $
          (:project-dir $)
          (ex-ctx/make-job-execution-context $)
          (job-ex/make-job-execution $)
          (jobs-desc/schedule-job! jd $))))
