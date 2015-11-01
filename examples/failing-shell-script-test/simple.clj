(ns fhofherr.simple.projectdef
  (:require [fhofherr.simple.dsl :refer :all]))

(defjob run-tests
  :test (execute "run_tests.sh"))
