(ns fhofherr.simple.test.engine.config-test
  (:require [clojure.test :refer :all]
            [fhofherr.simple.engine [config :as config]]
            [fhofherr.simple.engine.jobs [job-fn :as job-fn]]))

(def first-job (job-fn/make-job {:test identity}))
(def second-job (job-fn/make-job {:test identity}))

(deftest find-ci-jobs
  (testing "finds ci jobs in the given namespace"
    (let [found-jobs (config/find-ci-jobs
                       (the-ns 'fhofherr.simple.test.engine.config-test))]
      (is (= #{['first-job #'first-job]
               ['second-job #'second-job]}
             (set found-jobs))))))
