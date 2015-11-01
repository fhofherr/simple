(ns fhofherr.simple.test.engine-test
  (:require [clojure.test :refer :all]
            [fhofherr.simple.dsl :as dsl]
            [fhofherr.simple.engine :as engine]))


(def first-job
  (engine/make-job
    {:test (fn [job-context] (assoc job-context :first-job-executed true))}))

(def second-job
  (engine/make-job
    {:test (fn [job-context] (assoc job-context :second-job-executed true))}))


(deftest make-job

  (testing "ci jobs have :ci-job? in their meta data"
    (is (:ci-job? (meta first-job)))
    (is (engine/simple-ci-job? first-job)))

  (testing "ci jobs are functions of a job-context"
    (let [resulting-job-context (first-job {})]
      (is (true? (:first-job-executed resulting-job-context))))))

(deftest find-ci-jobs

  (testing "finds ci jobs in the given namespace"
    (let [found-jobs (engine/find-ci-jobs (find-ns 'fhofherr.simple.test.engine-test))]
      (is (= #{#'first-job #'second-job} found-jobs)))))
