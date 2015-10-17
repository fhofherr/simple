(ns fhofherr.simple.project
  (:require [clojure.java.shell :refer [sh]]))

(defn execute
  [executable]
  (fn [job-context]
    (sh (str (:project-dir job-context) "/" executable))))

(defmacro defjob
  [job-name & {:keys [test]}]
  `(defn ~job-name
     [job-context#]
     (when ~test
       (~test job-context#))))
