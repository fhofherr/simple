(ns fhofherr.simple.test.core.config-test
  (:require [clojure.test :refer :all]
            [fhofherr.simple.core [config :as config]
             [job-fn :as job-fn]]))

(def first-job (job-fn/make-job-fn {:test (job-fn/make-job-step-fn "test"
                                                                   identity)}))
(def second-job (job-fn/make-job-fn {:test (job-fn/make-job-step-fn "test"
                                                                    identity)}))

(deftest find-ci-jobs
  (testing "finds ci jobs in the given namespace"
    (let [found-jobs (config/find-ci-jobs
                      (the-ns 'fhofherr.simple.test.core.config-test)
                      job-fn/job-fn?)]
      (is (= #{['first-job #'first-job]
               ['second-job #'second-job]}
             (set found-jobs))))))
