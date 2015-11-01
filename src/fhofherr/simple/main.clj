(ns fhofherr.simple.main
  (:require [clojure.java.io :as io]
            [fhofherr.simple.engine :as engine])
  (:gen-class))

(defn- load-simple-clj
  [path]
  (load-file path)
  (find-ns 'fhofherr.simple.projectdef))

(defn run
  [project-dir]
  {:pre [(not-empty project-dir)
         (.isDirectory (io/as-file project-dir))]}
  (let [ci-job (-> (str project-dir "/simple.clj")
                   (load-simple-clj)
                   (engine/find-ci-jobs)
                   (first))]
    (if (= 0 (:exit (ci-job {:project-dir project-dir})))
     (println "Tests successful!")
     (println "Tests failed!"))))

(defn -main
  [& args]
  (try
    (run (first args))
    (finally
      (shutdown-agents))))
