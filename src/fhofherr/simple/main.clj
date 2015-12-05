(ns fhofherr.simple.main
  (:require [clojure.java.io :as io]
            [fhofherr.simple.core :as core]
            [fhofherr.simple.core.job-descriptor :as jobs])
  (:gen-class))

(defn start-simple-ci
  [project-dir]
  {:pre [(not-empty project-dir)
         (.isDirectory (io/as-file project-dir))]}
  (core/load-core project-dir "simple.clj"))

(defn run
  [project-dir]
  (let [core (start-simple-ci project-dir)
        ci-job (->> core
                    (:jobs)
                    (first))
        exec-id (core/start-job! core (first ci-job))]
    (await-for 60000 (:executor (second ci-job)))
    (if-not (jobs/failed? (second ci-job))
      (println "Tests successful!")
      (println "Tests failed!"))))

(defn -main
  [& args]
  (try
    (run (first args))
    (finally
      (shutdown-agents))))
