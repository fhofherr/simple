(ns fhofherr.simple.test.core.job-descriptor-test
  (:require [clojure.test :refer :all]
            [fhofherr.simple.core [job-descriptor :as jobs]
             [status-model :as sm]
             [job-fn :as job-fn]
             [job-execution-context :as ex-ctx]
             [job-execution :as job-ex]])
  (:import [java.util.concurrent CountDownLatch]))

(def successful-job (job-fn/make-job-fn
                     {:test (job-fn/make-job-step-fn
                             "test"
                             identity)}))

(def failing-job (job-fn/make-job-fn {:test (job-fn/make-job-step-fn
                                             "test"
                                             ex-ctx/mark-failed)}))

(def waiting-job-latch (atom (CountDownLatch. 1)))
(def waiting-job (job-fn/make-job-fn {:test (job-fn/make-job-step-fn
                                             "test"
                                             (fn [ctx]
                                               {:pre [@waiting-job-latch]}
                                               (.await @waiting-job-latch)
                                               ctx))}))

(def initial-ctx (ex-ctx/make-job-execution-context
                  "./path/to/non-existent/dir"))

(deftest make-job-descriptor

  (testing "creates a job descriptor for a job var"
    (let [job-desc (jobs/make-job-descriptor #'successful-job)]
      (is (= #'successful-job (:job-var job-desc)))
      (is (= successful-job (:job-fn job-desc)))
      (is (= [] @(:executions job-desc)))
      (is (= -1 @(:executor job-desc))))))

(deftest add-job-execution!

  (testing "add job execution to job descriptor"
    (let [job-desc (jobs/make-job-descriptor #'successful-job)
          exec (job-ex/make-job-execution initial-ctx)
          exec-id (jobs/add-job-execution! job-desc exec)]
      (is (= exec (get @(:executions job-desc) exec-id))))))

(deftest alter-job-execution!
  (let [job-desc (jobs/make-job-descriptor #'successful-job)
        exec (job-ex/make-job-execution initial-ctx)
        exec-id (jobs/add-job-execution! job-desc exec)]

    (testing "f must return a job execution"
      (is (thrown? AssertionError
                   (jobs/alter-job-execution! job-desc
                                              exec-id
                                              (constantly "Not a job execution")))))

    (testing "f is applied outside a transaction"
      (jobs/alter-job-execution! job-desc
                                 exec-id
                                 #(io! (identity %)))
      (is (= exec (jobs/get-job-execution job-desc exec-id)))))

  ;; Since this actually alters the job-desc we create a new one here.
  (testing "exec is replaced by f's result"
    (let [job-desc (jobs/make-job-descriptor #'successful-job)
          exec (job-ex/make-job-execution initial-ctx)
          exec-id (jobs/add-job-execution! job-desc exec)
          alter-exec #(assoc % :altered true)]
      (jobs/alter-job-execution! job-desc exec-id alter-exec)
      (is (true? (:altered (jobs/get-job-execution job-desc exec-id)))))))

(deftest schedule-jobs
  (testing "set a queued and an executing job's status"
    (reset! waiting-job-latch (CountDownLatch. 1))

    (let [job-desc (jobs/make-job-descriptor #'waiting-job)
          exec (job-ex/make-job-execution initial-ctx)
          exec-id-1 (jobs/schedule-job! job-desc exec)
          exec-id-2 (jobs/schedule-job! job-desc exec)]

      (Thread/sleep 100)
      ;; The first execution is executing; the second execution is queued.
      (is (job-ex/executing? (jobs/get-job-execution job-desc exec-id-1)))
      (is (job-ex/queued? (jobs/get-job-execution job-desc exec-id-2)))

      (.countDown @waiting-job-latch)
      (await-for 5000 (:executor job-desc))

      ;; After the completion of both executions the job-descriptor is
      ;; marked as successful.
      (is (job-ex/finished? (jobs/get-job-execution job-desc exec-id-1)))
      (is (job-ex/finished? (jobs/get-job-execution job-desc exec-id-2)))
      (is (jobs/successful? job-desc))))

  (testing "mark execution and descriptor as failed if the job fails"
    (let [job-desc (jobs/make-job-descriptor #'failing-job)
          exec (job-ex/make-job-execution initial-ctx)
          exec-id (jobs/schedule-job! job-desc exec)]
      (await-for 5000 (:executor job-desc))
      (is (job-ex/finished? (jobs/get-job-execution job-desc exec-id)))
      (is (jobs/failed? job-desc)))))
