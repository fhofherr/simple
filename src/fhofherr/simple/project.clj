(ns fhofherr.simple.project
  (:require [clojure.java.shell :refer [sh]]))

(defn execute
  [executable]
  (fn [project-map]
    (sh (str (:project-dir project-map) "/" executable))))
