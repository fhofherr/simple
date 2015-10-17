(ns fhofherr.simple.project
  (:require [clojure.java.shell :refer [sh]]))

(defn execute
  [executable]
  (fn [job-context]
    (sh (str (:project-dir job-context) "/" executable))))

(defmacro defjob
  [job-name & {:keys [test]}]
  `(defn ~(vary-meta job-name assoc :ci-job? true)
     [job-context#]
     (when ~test
       (~test job-context#))))
