(ns fhofherr.simple.test.engine-test
  (:require [clojure.test :refer :all]
            [fhofherr.simple.dsl :as dsl]
            [fhofherr.simple.engine :as engine])
  (:import [java.util.concurrent CountDownLatch]))

(deftest execution-context

  (testing "the initial context contains the project directory"
    (let [project-dir "path/to/some/directory"]
      (is (= project-dir (:project-dir (engine/initial-context project-dir))))))

  (testing "the initial context is never failed"
    (is (not (engine/failed? (engine/initial-context ".")))))

  (testing "the context can be marked as failed"
    (is (engine/failed? (engine/mark-failed (engine/initial-context "."))))))

(defn- register-execution
  [marker]
  (fn [ctx]
    (update-in ctx [:payload :executions] #(conj (or % []) marker))))

(deftest make-job

  (testing "ci jobs have :ci-job? in their meta data"
    (let [job (engine/make-job {:test identity})]
      (is (:ci-job? (meta job)))
      (is (engine/simple-ci-job? job))))

  (testing "ci jobs are functions of a job context"
    (let [job (engine/make-job {:test (register-execution :job)})
          new-ctx (job (engine/initial-context "."))]
      (is (= [:job] (get-in new-ctx [:payload :executions])))))

  (testing "ci jobs execute their `:before` function before the `:test`"
    (let [job (engine/make-job {:before (register-execution :before)
                                :test (register-execution :test)})
          new-ctx (job (engine/initial-context "."))]
      (is (= [:before :test] (get-in new-ctx [:payload :executions])))))

  (testing "the `:test` is not executed if `:before` fails the job"
    (let [job (engine/make-job {:before (comp
                                          (register-execution :before)
                                          #(engine/mark-failed %))
                                :test (register-execution :test)})
          new-ctx (job (engine/initial-context "."))]
      (is (= [:before] (get-in new-ctx [:payload :executions])))))

  (testing "ci jobs execute their `:after` function after the `:test`"
    (let [job (engine/make-job {:test (register-execution :test)
                                :after (register-execution :after)})
          new-ctx (job (engine/initial-context "."))]
      (is (= [:test :after] (get-in new-ctx [:payload :executions])))))

  (testing "`:after` is executed even upon failure"
    (let [job (engine/make-job {:before (comp
                                          (register-execution :before)
                                          #(engine/mark-failed %))
                                :test (register-execution :test)
                                :after (register-execution :after)})
          new-ctx (job (engine/initial-context "."))]
      (is (= [:before :after] (get-in new-ctx [:payload :executions]))))

    (let [job (engine/make-job {:before (register-execution :before)
                                :test (comp
                                        (register-execution :test)
                                        #(engine/mark-failed %))
                                :after (register-execution :after)})
          new-ctx (job (engine/initial-context "."))]
      (is (= [:before :test :after] (get-in new-ctx [:payload :executions]))))))

(def successful-job (engine/make-job {:test engine/mark-successful}))

(def failing-job (engine/make-job {:test engine/mark-failed}))

(def waiting-job-latch (atom (CountDownLatch. 1)))
(def waiting-job (engine/make-job {:test (fn [ctx]
                                           {:pre [@waiting-job-latch]}
                                           (.await @waiting-job-latch)
                                           (engine/mark-successful ctx))}))


(def throwing-job (engine/make-job {:test (fn [ctx]
                                            (throw (Throwable. "Kaboom, Baby!")))}))

(deftest find-ci-jobs

  (testing "finds ci jobs in the given namespace"
    (let [found-jobs (engine/find-ci-jobs (find-ns 'fhofherr.simple.test.engine-test))]
      (is (= #{#'successful-job
               #'failing-job
               #'waiting-job
               #'throwing-job}
             (set found-jobs))))))

(deftest make-job-descriptor

  (testing "creates a job descriptor for a job var"
    (let [job-desc (engine/make-job-descriptor #'successful-job)]
      (is (= #'successful-job (:job-var job-desc)))
      (is (= successful-job (:job-fn job-desc)))
      (is (= [] @(:executions job-desc)))
      (is (= -1 @(:executor job-desc)))
      (is (engine/created? job-desc))
      (is (not (engine/queued? job-desc)))
      (is (not (engine/executing? job-desc)))
      (is (not (engine/successful? job-desc)))
      (is (not (engine/failed? job-desc))))))

(deftest make-job-execution!

  (testing "create a new job descriptor"
    (let [prj-dir "./path/to/non-existent/dir"
          job-desc (engine/make-job-descriptor #'successful-job)
          [exec-id exec] (engine/make-job-execution! job-desc
                                              (engine/initial-context prj-dir))]
      (is (engine/created? exec)))))

(deftest schedule-jobs
  (let [prj-dir "./path/to/non-existent/dir"
        ctx (engine/initial-context prj-dir)]

    (testing "set a queued and an executing job's status"
      (reset! waiting-job-latch (CountDownLatch. 1))

      (let [job-desc (engine/make-job-descriptor #'waiting-job)
            exec-id-1 (engine/schedule-job! job-desc ctx)
            exec-id-2 (engine/schedule-job! job-desc ctx)]

        (Thread/sleep 100)
        ;; The first execution is executing; the second execution is queued.
        (is (engine/executing? (engine/get-job-execution job-desc exec-id-1)))
        (is (engine/queued? (engine/get-job-execution job-desc exec-id-2)))

        ;; The job descriptor has an executing and a queued execution and is
        ;; thus executing and queued at the same time.
        (is (engine/executing? job-desc))
        (is (engine/queued? job-desc))

        (.countDown @waiting-job-latch)
        (await-for 5000 (:executor job-desc))

        ;; After the completion of both executions the individual executions,
        ;; as well as the job-descriptor are marked as successful.
        (is (engine/successful? (engine/get-job-execution job-desc exec-id-1)))
        (is (engine/successful? (engine/get-job-execution job-desc exec-id-2)))
        (is (engine/successful? job-desc))))

    (testing "mark execution and descriptor as failed if the job fails"
      (let [job-desc (engine/make-job-descriptor #'failing-job)
            exec-id (engine/schedule-job! job-desc ctx)]
        (await-for 5000 (:executor job-desc))
        (is (engine/failed? (engine/get-job-execution job-desc exec-id)))
        (is (engine/failed? job-desc))))

    (testing "mark execution and descriptor as failed if the job throws"
      (let [job-desc (engine/make-job-descriptor #'throwing-job)
            exec-id (engine/schedule-job! job-desc ctx)]
        (await-for 5000 (:executor job-desc))
        (is (engine/failed? (engine/get-job-execution job-desc exec-id)))
        (is (engine/failed? job-desc))))))
