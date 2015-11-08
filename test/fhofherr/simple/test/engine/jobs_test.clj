(ns fhofherr.simple.test.engine.jobs-test
  (:require [clojure.test :refer :all]
            [fhofherr.simple.engine [jobs :as jobs]
                                    [status-model :as sm]])
  (:import [java.util.concurrent CountDownLatch]))

(deftest execution-context

  (testing "the initial context contains the project directory"
    (let [project-dir "path/to/some/directory"]
      (is (= project-dir (:project-dir (jobs/initial-context project-dir))))))

  (testing "the initial context is never failed"
    (is (not (sm/failed? (jobs/initial-context ".")))))

  (testing "the context can be marked as failed"
    (is (sm/failed? (sm/mark-failed (jobs/initial-context "."))))))

(defn- register-execution
  [marker]
  (fn [ctx]
    (update-in ctx [:payload :executions] #(conj (or % []) marker))))

(deftest make-job

  (testing "ci jobs have :ci-job? in their meta data"
    (let [job (jobs/make-job {:test identity})]
      (is (:ci-job? (meta job)))
      (is (jobs/simple-ci-job? job))))

  (testing "ci jobs are functions of a job context"
    (let [job (jobs/make-job {:test (register-execution :job)})
          new-ctx (job (jobs/initial-context "."))]
      (is (= [:job] (get-in new-ctx [:payload :executions])))))

  (testing "ci jobs execute their `:before` function before the `:test`"
    (let [job (jobs/make-job {:before (register-execution :before)
                                :test (register-execution :test)})
          new-ctx (job (jobs/initial-context "."))]
      (is (= [:before :test] (get-in new-ctx [:payload :executions])))))

  (testing "the `:test` is not executed if `:before` fails the job"
    (let [job (jobs/make-job {:before (comp
                                          (register-execution :before)
                                          #(sm/mark-failed %))
                                :test (register-execution :test)})
          new-ctx (job (jobs/initial-context "."))]
      (is (= [:before] (get-in new-ctx [:payload :executions])))))

  (testing "ci jobs execute their `:after` function after the `:test`"
    (let [job (jobs/make-job {:test (register-execution :test)
                                :after (register-execution :after)})
          new-ctx (job (jobs/initial-context "."))]
      (is (= [:test :after] (get-in new-ctx [:payload :executions])))))

  (testing "`:after` is executed even upon failure"
    (let [job (jobs/make-job {:before (comp
                                          (register-execution :before)
                                          #(sm/mark-failed %))
                                :test (register-execution :test)
                                :after (register-execution :after)})
          new-ctx (job (jobs/initial-context "."))]
      (is (= [:before :after] (get-in new-ctx [:payload :executions]))))

    (let [job (jobs/make-job {:before (register-execution :before)
                                :test (comp
                                        (register-execution :test)
                                        #(sm/mark-failed %))
                                :after (register-execution :after)})
          new-ctx (job (jobs/initial-context "."))]
      (is (= [:before :test :after] (get-in new-ctx [:payload :executions]))))))

(def successful-job (jobs/make-job {:test sm/mark-successful}))

(def failing-job (jobs/make-job {:test sm/mark-failed}))

(def waiting-job-latch (atom (CountDownLatch. 1)))
(def waiting-job (jobs/make-job {:test (fn [ctx]
                                           {:pre [@waiting-job-latch]}
                                           (.await @waiting-job-latch)
                                           (sm/mark-successful ctx))}))

(def throwing-job (jobs/make-job {:test (fn [ctx]
                                            (throw (Throwable. "Kaboom, Baby!")))}))

(deftest make-job-descriptor

  (testing "creates a job descriptor for a job var"
    (let [job-desc (jobs/make-job-descriptor #'successful-job)]
      (is (= #'successful-job (:job-var job-desc)))
      (is (= successful-job (:job-fn job-desc)))
      (is (= [] @(:executions job-desc)))
      (is (= -1 @(:executor job-desc)))
      (is (sm/created? job-desc))
      (is (not (sm/queued? job-desc)))
      (is (not (sm/executing? job-desc)))
      (is (not (sm/successful? job-desc)))
      (is (not (sm/failed? job-desc))))))

(deftest make-job-execution!

  (testing "create a new job descriptor"
    (let [prj-dir "./path/to/non-existent/dir"
          job-desc (jobs/make-job-descriptor #'successful-job)
          [exec-id exec] (jobs/make-job-execution! job-desc
                                              (jobs/initial-context prj-dir))]
      (is (sm/created? exec)))))

(deftest schedule-jobs
  (let [prj-dir "./path/to/non-existent/dir"
        ctx (jobs/initial-context prj-dir)]

    (testing "set a queued and an executing job's status"
      (reset! waiting-job-latch (CountDownLatch. 1))

      (let [job-desc (jobs/make-job-descriptor #'waiting-job)
            exec-id-1 (jobs/schedule-job! job-desc ctx)
            exec-id-2 (jobs/schedule-job! job-desc ctx)]

        (Thread/sleep 100)
        ;; The first execution is executing; the second execution is queued.
        (is (sm/executing? (jobs/get-job-execution job-desc exec-id-1)))
        (is (sm/queued? (jobs/get-job-execution job-desc exec-id-2)))

        ;; The job descriptor has an executing and a queued execution and is
        ;; thus executing and queued at the same time.
        (is (sm/executing? job-desc))
        (is (sm/queued? job-desc))

        (.countDown @waiting-job-latch)
        (await-for 5000 (:executor job-desc))

        ;; After the completion of both executions the individual executions,
        ;; as well as the job-descriptor are marked as successful.
        (is (sm/successful? (jobs/get-job-execution job-desc exec-id-1)))
        (is (sm/successful? (jobs/get-job-execution job-desc exec-id-2)))
        (is (sm/successful? job-desc))))

    (testing "mark execution and descriptor as failed if the job fails"
      (let [job-desc (jobs/make-job-descriptor #'failing-job)
            exec-id (jobs/schedule-job! job-desc ctx)]
        (await-for 5000 (:executor job-desc))
        (is (sm/failed? (jobs/get-job-execution job-desc exec-id)))
        (is (sm/failed? job-desc))))

    (testing "mark execution and descriptor as failed if the job throws"
      (let [job-desc (jobs/make-job-descriptor #'throwing-job)
            exec-id (jobs/schedule-job! job-desc ctx)]
        (await-for 5000 (:executor job-desc))
        (is (sm/failed? (jobs/get-job-execution job-desc exec-id)))
        (is (sm/failed? job-desc))))))
