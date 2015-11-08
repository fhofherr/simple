(ns fhofherr.simple.test.engine-test
  (:require [clojure.test :refer :all]
            [fhofherr.simple.engine :as engine]))

(deftest has-job?
  (let [e (engine/load-engine "examples/successful-shell-script-test" "simple.clj")]
    (is (true? (engine/has-job? e 'run-tests)))
    (is (true? (engine/has-job? e "run-tests")))
    (is (true? (engine/has-job? e :run-tests)))))
