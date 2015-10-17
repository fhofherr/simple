(ns fhofherr.simple.main
  (:require [clojure.java.io :as io]
            [fhofherr.simple.project :as prj])
  (:gen-class))

(defn run
  [project-dir]
  {:pre [(not-empty project-dir)
         (.isDirectory (io/as-file project-dir))]}
  (if (= 0 (:exit ((prj/execute "run_tests.sh") {:project-dir project-dir})))
    (println "Tests successful!")
    (println "Tests failed!")))

(defn -main
  [& args]
  (try
    (run (first args))
    (finally
      (shutdown-agents))))
