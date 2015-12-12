(ns fhofherr.simple.core
  "Simple CI's application core."
  (:require [fhofherr.simple.core.dsl] ; Required to pass it to load-config
            [fhofherr.simple.core [config :as config]
             [job-fn :as job-fn]
             [job-descriptor :as jobs]
             [job-execution :as job-ex]
             [job-execution-context :as ex-ctx]
             [subsystems :as subsystems]]))

(defn- configure-jobs-subsystem
  [cidef-ns]
  (as-> cidef-ns $
        (config/filter-publics $ job-fn/job-fn?)
        (map (fn [[s v]] [(name s) (jobs/make-job-descriptor (name s)
                                                             (var-get v))]) $)
        (into {} $)))

(defn load-core
  "Bootstrap Simple CI using the `config-file` contained in `project-dir`."
  [project-dir config-file]
  (let [cidef-ns (as-> (str project-dir "/" config-file) $
                       (config/load-config (the-ns 'fhofherr.simple.core.dsl) $))
        jobs (configure-jobs-subsystem cidef-ns)]
    (-> {:project-dir project-dir
         ::started false}
        (subsystems/register-subsystem :jobs jobs))))

(defn start-core
  [core]
  (-> core
      (subsystems/start)
      (assoc ::started true)))

(defn stop-core
  [core]
  (-> core
      (subsystems/stop)
      (assoc ::started false)))

(defn has-job?
  "Check if the `core` has a job named `job-name`."
  [core job-name]
  (contains? (:jobs core) (name job-name)))

(defn started?
  [core]
  (::started core))

(defn start-job!
  "Start the job named `job-name`."
  [core job-name]
  {:pre [(started? core)
         (has-job? core job-name)]}
  (if-let [jd (get-in core [:jobs (name job-name)])]
    (as-> core $
          (:project-dir $)
          (ex-ctx/make-job-execution-context $)
          (job-ex/make-job-execution $)
          (jobs/schedule-job! jd $))))
