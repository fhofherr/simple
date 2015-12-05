(ns fhofherr.simple.test.core.job-execution-test
  (:require [clojure.test :refer :all]
            [fhofherr.simple.core [status-model :as sm]
             [job-execution-context :as ex-ctx]
             [job-execution :as job-ex]]))

(def initial-ctx (-> "path/to/some/directory"
                     (ex-ctx/make-job-execution-context)))
(deftest job-execution
  (let [execution (job-ex/make-job-execution initial-ctx)]

    (testing "the execution references the context"
      (is (= initial-ctx (:context execution))))

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
               marks the initial-ctx as :successful"
        ;; Guard assertion to ensure we actually have an executing initial-ctx
        (is (ex-ctx/executing? (:context executing-exec)))
        (let [finished-exec (job-ex/mark-finished executing-exec)]
          (is (ex-ctx/successful? (:context finished-exec)))))

      (testing "marking an execution as finished whose context is failed leaves
               it unchanged"
        (let [executing-exec* (job-ex/update-context executing-exec
                                                     ex-ctx/mark-failed)
              finished-exec (job-ex/mark-finished executing-exec*)]
          (is (ex-ctx/failed? (:context finished-exec))))))))

(deftest update-context

  (let [execution (job-ex/make-job-execution initial-ctx)]

    (testing "f must return a job execution context"
      (is (thrown? AssertionError
                   (job-ex/update-context execution (constantly "not a context")))))

    (testing "the executions old context is replaced by f's return value"
      (let [new-exec (job-ex/update-context execution
                                            #(assoc % :altered %2)
                                            :something-new)]
        (is (= :something-new (get-in new-exec [:context :altered])))))))
