(ns fhofherr.simple.test.project-test
  (:require [clojure.test :refer :all]
            [fhofherr.clj-io.files :as files]
            [fhofherr.simple.project :as prj]))

(deftest execute-a-shell-script
  (files/with-tmp-dir
    [path]
    (let [script-path (-> "tests/execute-shell-script.sh"
                          (files/copy-resource (.resolve path "execute-shell-script.sh"))
                          (files/chmod "rwx")
                          (.getFileName)
                          (str))
          result ((prj/execute script-path) {:project-dir path})]
      (is (= 0 (:exit result))))))

(prj/defjob unit-test-job
  :test (fn [job-context] (assoc job-context :unit-test-job-executed true)))

(deftest define-a-ci-job

  (testing "ci jobs have :ci-job? in their meta data"
    (is (:ci-job? (meta #'unit-test-job))))

  (testing "ci jobs are functions of a job-context"
    (let [resulting-job-context (unit-test-job {})]
      (is (true? (:unit-test-job-executed resulting-job-context))))))
