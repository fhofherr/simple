(ns fhofherr.simple.test.engine-test
  (:require [clojure.test :refer :all]
            [fhofherr.simple.project :as prj]
            [fhofherr.simple.engine :as engine]))

(prj/defjob first-job
  :test (fn [job-context] (assoc job-context :first-job-executed true)))

(prj/defjob second-job
  :test (fn [job-context] (assoc job-context :second-job-executed true)))

(deftest find-ci-jobs
  (testing "finds ci jobs in the current namespace"
    (let [found-jobs (engine/find-ci-jobs (find-ns 'fhofherr.simple.test.engine-test))]
      (is (= #{#'first-job #'second-job} found-jobs)))))
