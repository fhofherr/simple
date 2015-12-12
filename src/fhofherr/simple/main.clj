(ns fhofherr.simple.main
  (:require [clojure.java.io :as io]
            [fhofherr.simple.core :as core]
            [fhofherr.simple.core.job-descriptor :as jobs])
  (:gen-class))

(defn start-simple-ci
  [project-dir]
  {:pre [(not-empty project-dir)
         (.isDirectory (io/as-file project-dir))]}
  (-> (core/load-core project-dir "simple.clj")
      (core/start-core)))

(defn -main
  [& args]
  (let [core (start-simple-ci (first args))]
    (doto (Runtime/getRuntime)
      (.addShutdownHook (Thread. (fn []
                                   (try
                                     (core/stop-core core)
                                     (finally
                                       (shutdown-agents)))))))
    (while true
      (try
        (Thread/sleep 60000)
        (catch Throwable t)))))
