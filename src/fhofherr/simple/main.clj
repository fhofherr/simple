(ns fhofherr.simple.main
  (:require [clojure.java.io :as io]
            [fhofherr.simple.engine :as engine]
            [fhofherr.simple.engine.jobs :as jobs])
  (:gen-class))

(defn start-simple-ci
  [project-dir]
  {:pre [(not-empty project-dir)
         (.isDirectory (io/as-file project-dir))]}
  (engine/load-engine project-dir "simple.clj"))

(defn run
  [project-dir]
  (let [engine (start-simple-ci project-dir)
        ci-job (->> engine
                   (:jobs)
                   (first))
        exec-id (engine/start-job! engine (first ci-job))]
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
