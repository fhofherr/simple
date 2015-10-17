(ns fhofherr.simple.main
  (:require [clojure.java.shell :refer [sh]]
            [clojure.java.io :as io])
  (:gen-class))

(defn run
  [project-dir]
  {:pre [(not-empty project-dir)
         (.isDirectory (io/as-file project-dir))]}
  (if (= 0 (:exit (sh (str project-dir "/run_tests.sh"))))
    (println "Tests successful!")
    (println "Tests failed!")))

(defn -main
  [& args]
  (try
    (run (first args))
    (finally
      (shutdown-agents))))
