(ns fhofherr.simple.test.dsl-test
  (:require [clojure.test :refer :all]
            [fhofherr.clj-io.files :as files]
            [fhofherr.simple.dsl :as dsl]
            [fhofherr.simple.engine :as engine]))

(deftest execute-a-shell-script
  (files/with-tmp-dir
    [path]
    (let [script-path (-> "tests/execute-shell-script.sh"
                          (files/copy-resource (.resolve path "execute-shell-script.sh"))
                          (files/chmod "rwx")
                          (.getFileName)
                          (str))
          result ((dsl/execute script-path) {:project-dir path})]
      (is (= 0 (:exit result))))))

(dsl/defjob unit-test-job
  :test (fn [job-context] (assoc job-context :unit-test-job-executed true)))

(deftest define-a-ci-job 

  (testing "defines a Simple CI job"
    (is (engine/simple-ci-job? unit-test-job))))
