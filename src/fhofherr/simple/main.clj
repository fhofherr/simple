(ns fhofherr.simple.main
  (:require [clojure.java.io :as io]
            [fhofherr.simple.engine :as engine])
  (:gen-class))

(defn run
  [project-dir]
  {:pre [(not-empty project-dir)
         (.isDirectory (io/as-file project-dir))]}
  (let [ci-job (-> (str project-dir "/simple.clj")
                   (engine/load-config)
                   (engine/find-ci-jobs)
                   (first)
                   (engine/make-job-descriptor))
        exec-id (engine/schedule-job! ci-job
                                      (engine/initial-context project-dir))]
    (await-for 60000 (:executor ci-job))
    (if-not (engine/failed? ci-job)
      (println "Tests successful!")
      (println "Tests failed!"))))

(defn -main
  [& args]
  (try
    (run (first args))
    (finally
      (shutdown-agents))))
