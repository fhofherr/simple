(ns fhofherr.simple.main
  (:require [clojure.java.io :as io]
            [fhofherr.simple.engine.jobs :as jobs]
            [fhofherr.simple.engine.config :as config]
            [fhofherr.simple.engine.status-model :as sm])
  (:gen-class))

(defn run
  [project-dir]
  {:pre [(not-empty project-dir)
         (.isDirectory (io/as-file project-dir))]}
  (let [ci-job (-> (str project-dir "/simple.clj")
                   (config/load-config)
                   (config/find-ci-jobs)
                   (first)
                   (jobs/make-job-descriptor))
        exec-id (jobs/schedule-job! ci-job
                                      (jobs/initial-context project-dir))]
    (await-for 60000 (:executor ci-job))
    (if-not (sm/failed? ci-job)
      (println "Tests successful!")
      (println "Tests failed!"))))

(defn -main
  [& args]
  (try
    (run (first args))
    (finally
      (shutdown-agents))))
