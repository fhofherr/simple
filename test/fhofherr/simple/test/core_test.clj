(ns fhofherr.simple.test.core-test
  (:require [clojure.test :refer :all]
            [fhofherr.simple.core :as core]))

(deftest has-job?
  (let [e (core/load-core "examples/successful-shell-script-test" "simple.clj")]
    (is (true? (core/has-job? e 'run-tests)))
    (is (true? (core/has-job? e "run-tests")))
    (is (true? (core/has-job? e :run-tests)))))
