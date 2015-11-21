(ns fhofherr.simple.test.engine.jobs.job-execution-test
  (:require [clojure.test :refer :all]
            [fhofherr.simple.engine.status-model :as sm]
            [fhofherr.simple.engine.jobs [execution-context :as ex-ctx]
                                         [job-execution :as job-ex]]))

(deftest job-execution

  (let [project-dir "path/to/some/directory"
        context (ex-ctx/make-job-execution-context project-dir)
        execution (job-ex/make-job-execution context)]

    (testing "the execution references the context"
      (is (= context (:context execution))))

    (testing "the new executions state is created"
      (is (job-ex/created? execution)))

    (testing "a newly created execution can be marked as queued"
      (is (job-ex/queued? (job-ex/mark-queued execution))))

    (testing "marking an execution as queued leaves its context unchanged"
      (let [old-ctx-state (sm/current-state (:context execution))
            queued-exec (job-ex/mark-queued execution)
            new-ctx-state (sm/current-state (:context queued-exec))]
        (is (= old-ctx-state new-ctx-state))))

    (let [executing-exec (-> execution
                             (job-ex/mark-queued)
                             (job-ex/mark-executing))]

      (testing "a queued execution can be marked as executing"
        (is (job-ex/executing? executing-exec)))

      (testing "marking an execution executing marks its context executing as
               well"
        (is (ex-ctx/executing? (:context executing-exec))))

      (testing "a executing execution can be marked as finished"
        (let [finished-exec (job-ex/mark-finished executing-exec)]
          (is (job-ex/finished? finished-exec))))

      (testing "marking an execution as finished whose context is executing
               marks the context as :successful"
        ;; Guard assertion to ensure we actually have an executing context
        (is (ex-ctx/executing? (:context executing-exec)))
        (let [finished-exec (job-ex/mark-finished executing-exec)]
          (is (ex-ctx/successful? (:context finished-exec)))))

      (testing "marking an execution as finished whose context is failed leaves
               it unchanged"
        (let [executing-exec* (update-in executing-exec
                                         [:context]
                                         ex-ctx/mark-failed)
              finished-exec (job-ex/mark-finished executing-exec*)]
          (is (ex-ctx/failed? (:context finished-exec))))))))
