(ns fhofherr.simple.projectdef
  (:require [fhofherr.simple.project :refer :all]))

(defjob run-tests
  :test (execute "run_tests.sh"))
